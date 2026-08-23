import '@testing-library/jest-dom'

const storage = new Map<string, string>()

Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, value)
    },
    removeItem: (key: string) => {
      storage.delete(key)
    },
    clear: () => {
      storage.clear()
    },
  },
  writable: true,
})

// Recharts uses ResizeObserver + getBoundingClientRect to size ResponsiveContainer.
// jsdom does not implement either, so we stub them with fixed 500×300 dimensions.
class FakeResizeObserver {
  private callback: ResizeObserverCallback
  constructor(cb: ResizeObserverCallback) {
    this.callback = cb
  }
  observe(target: Element) {
    this.callback(
      [{ target, contentRect: { width: 500, height: 300, top: 0, left: 0, bottom: 300, right: 500, x: 0, y: 0, toJSON: () => ({}) } } as ResizeObserverEntry],
      this as unknown as ResizeObserver,
    )
  }
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = FakeResizeObserver as unknown as typeof ResizeObserver

Element.prototype.getBoundingClientRect = function () {
  return { width: 500, height: 300, top: 0, left: 0, bottom: 300, right: 500, x: 0, y: 0, toJSON: () => ({}) } as DOMRect
}
