import { db, type QueuedOp } from '@/lib/db'
import { sessionService } from './sessionService'
import type {
  AddSetLogRequest,
  BatchSetLogOperationRequest,
  BatchSetLogResponse,
  SetLog,
  UpdateSetLogRequest,
} from './sessionService'

const MAX_RETRIES = 3

type EnqueuePayload =
  | { type: 'add-set'; sessionId: string; data: AddSetLogRequest; clientSetLogId?: string }
  | { type: 'update-set'; sessionId: string; setLogId: string; data: UpdateSetLogRequest }
  | { type: 'delete-set'; sessionId: string; setLogId: string }

export type FlushSucceededReceipt =
  | {
      type: 'add-set'
      sessionId: string
      queuedOpId: number
      clientSetLogId: string | null
      data: AddSetLogRequest
      serverSetLog: SetLog
    }
  | {
      type: 'update-set'
      sessionId: string
      queuedOpId: number
      setLogId: string
      data: UpdateSetLogRequest
    }
  | {
      type: 'delete-set'
      sessionId: string
      queuedOpId: number
      setLogId: string
    }

export type FlushFailedReceipt =
  | {
      type: 'add-set'
      sessionId: string
      queuedOpId: number
      clientSetLogId: string | null
      data: AddSetLogRequest
      exhausted: boolean
    }
  | {
      type: 'update-set'
      sessionId: string
      queuedOpId: number
      setLogId: string
      data: UpdateSetLogRequest
      exhausted: boolean
    }
  | {
      type: 'delete-set'
      sessionId: string
      queuedOpId: number
      setLogId: string
      exhausted: boolean
    }

export interface FlushResult {
  succeeded: FlushSucceededReceipt[]
  failed: FlushFailedReceipt[]
}

function getQueuedOpId(op: QueuedOp): number {
  if (op.id === undefined) {
    throw new Error('Queued offline operation is missing its Dexie id.')
  }

  return op.id
}

function getClientSetLogId(op: QueuedOp): string | null {
  if (op.type !== 'add-set') {
    return null
  }

  return (op as QueuedOp & { clientSetLogId?: string }).clientSetLogId ?? null
}

function toSucceededReceipt(
  op: QueuedOp,
  result: Awaited<ReturnType<typeof sessionService.addSetLog>> | void,
): FlushSucceededReceipt {
  const queuedOpId = getQueuedOpId(op)

  if (op.type === 'add-set') {
    return {
      type: 'add-set',
      sessionId: op.sessionId,
      queuedOpId,
      clientSetLogId: getClientSetLogId(op),
      data: op.data,
      serverSetLog: result as SetLog,
    }
  }

  if (op.type === 'update-set') {
    return {
      type: 'update-set',
      sessionId: op.sessionId,
      queuedOpId,
      setLogId: op.setLogId,
      data: op.data,
    }
  }

  return {
    type: 'delete-set',
    sessionId: op.sessionId,
    queuedOpId,
    setLogId: op.setLogId,
  }
}

function toFailedReceipt(op: QueuedOp, exhausted: boolean): FlushFailedReceipt {
  const queuedOpId = getQueuedOpId(op)

  if (op.type === 'add-set') {
    return {
      type: 'add-set',
      sessionId: op.sessionId,
      queuedOpId,
      clientSetLogId: getClientSetLogId(op),
      data: op.data,
      exhausted,
    }
  }

  if (op.type === 'update-set') {
    return {
      type: 'update-set',
      sessionId: op.sessionId,
      queuedOpId,
      setLogId: op.setLogId,
      data: op.data,
      exhausted,
    }
  }

  return {
    type: 'delete-set',
    sessionId: op.sessionId,
    queuedOpId,
    setLogId: op.setLogId,
    exhausted,
  }
}

function toBatchOperation(op: QueuedOp): BatchSetLogOperationRequest {
  if (op.type === 'add-set') {
    return {
      type: 'add-set',
      exerciseId: op.data.exerciseId,
      setNumber: op.data.setNumber,
      weight: op.data.weight,
      reps: op.data.reps,
      rir: op.data.rir,
    }
  }

  if (op.type === 'update-set') {
    return {
      type: 'update-set',
      setLogId: op.setLogId,
      weight: op.data.weight,
      reps: op.data.reps,
      rir: op.data.rir,
    }
  }

  return {
    type: 'delete-set',
    setLogId: op.setLogId,
  }
}

async function dispatchQueuedOp(op: QueuedOp): Promise<SetLog | void> {
  if (op.type === 'add-set') {
    return sessionService.addSetLog(op.sessionId, op.data)
  }

  if (op.type === 'update-set') {
    return sessionService.updateSetLog(op.sessionId, op.setLogId, op.data)
  }

  return sessionService.deleteSetLog(op.sessionId, op.setLogId)
}

async function flushOpsIndividually(ops: QueuedOp[]): Promise<FlushResult> {
  const results = await Promise.allSettled(ops.map((op) => dispatchQueuedOp(op)))

  return {
    succeeded: ops.flatMap((op, index) =>
      results[index].status === 'fulfilled'
        ? [toSucceededReceipt(op, results[index].value)]
        : [],
    ),
    failed: ops.flatMap((op, index) =>
      results[index].status === 'rejected' ? [toFailedReceipt(op, false)] : [],
    ),
  }
}

function validateBatchResponse(
  ops: QueuedOp[],
  response: BatchSetLogResponse,
): BatchSetLogResponse {
  if (response.results.length !== ops.length) {
    throw new Error('Batch response length did not match queued operation count.')
  }

  response.results.forEach((result, index) => {
    if (result.type !== ops[index].type) {
      throw new Error('Batch response order did not match queued operations.')
    }
    if (result.succeeded && ops[index].type === 'add-set' && result.setLog === null) {
      throw new Error('Successful add-set batch results must include the created SetLog.')
    }
  })

  return response
}

async function flushOpsAsBatch(ops: QueuedOp[]): Promise<FlushResult> {
  try {
    const response = validateBatchResponse(
      ops,
      await sessionService.batchSetLogs(
        ops[0].sessionId,
        ops.map((op) => toBatchOperation(op)),
      ),
    )

    return {
      succeeded: ops.flatMap((op, index) =>
        response.results[index].succeeded
          ? [toSucceededReceipt(op, response.results[index].setLog ?? undefined)]
          : [],
      ),
      failed: ops.flatMap((op, index) =>
        response.results[index].succeeded ? [] : [toFailedReceipt(op, false)],
      ),
    }
  } catch {
    return flushOpsIndividually(ops)
  }
}

function groupOpsBySession(ops: QueuedOp[]): QueuedOp[][] {
  const groups = new Map<string, QueuedOp[]>()

  ops.forEach((op) => {
    const current = groups.get(op.sessionId) ?? []
    current.push(op)
    groups.set(op.sessionId, current)
  })

  return [...groups.values()]
}

export const offlineQueue = {
  enqueue: (payload: EnqueuePayload) =>
    db.queuedOps.add({ ...payload, queuedAt: Date.now(), retryCount: 0 } as QueuedOp),

  getAll: () => db.queuedOps.toArray(),

  clear: () => db.queuedOps.clear(),

  /**
   * Attempts to replay all queued operations against the live API.
   *
   * Guarantees:
   * - Idempotent: calling flush() when the queue is empty is a no-op.
   * - Ordered: operations within each WorkoutSession are replayed in enqueue order.
   * - Partial-success: each operation is attempted independently; a failure in one
   *   does not prevent the others from being retried.
   * - Retry-capped: operations that have already failed MAX_RETRIES times are
   *   permanently dropped and counted as failed in the result.
   *
   * Expected call site: createFlushScheduler (via useOfflineSync) on reconnect.
   * Do not call from multiple concurrent triggers -- parallel flush() calls will
   * race on the same queue entries.
   */
  flush: async (): Promise<FlushResult> => {
    const ops = await db.queuedOps.toArray()

    if (ops.length === 0) {
      return { succeeded: [], failed: [] }
    }

    // Drop ops that have exceeded the retry cap -- they are permanently failed.
    const exceededOps = ops.filter((op) => op.retryCount >= MAX_RETRIES)
    const exceededIds = exceededOps.map((op) => getQueuedOpId(op))
    if (exceededIds.length > 0) {
      await Promise.all(exceededIds.map((id) => db.queuedOps.delete(id)))
    }

    const retryable = ops.filter((op) => op.retryCount < MAX_RETRIES)
    if (retryable.length === 0) {
      return { succeeded: [], failed: exceededOps.map((op) => toFailedReceipt(op, true)) }
    }
    const results: FlushResult[] = []
    for (const sessionOps of groupOpsBySession(retryable)) {
      results.push(
        sessionOps.length > 1
          ? await flushOpsAsBatch(sessionOps)
          : await flushOpsIndividually(sessionOps),
      )
    }
    const succeeded = results.flatMap((result) => result.succeeded)
    const failed = results.flatMap((result) => result.failed)

    await Promise.all(succeeded.map((receipt) => db.queuedOps.delete(receipt.queuedOpId)))
    await Promise.all(
      failed.map((receipt) => db.queuedOps.update(receipt.queuedOpId, { retryCount: retryable.find((op) => getQueuedOpId(op) === receipt.queuedOpId)!.retryCount + 1 })),
    )

    return {
      succeeded,
      failed: [...failed, ...exceededOps.map((op) => toFailedReceipt(op, true))],
    }
  },
}
