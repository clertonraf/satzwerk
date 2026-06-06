# Implementation Checklist

Practices derived from retrospectives. Apply these before raising a PR.

## SVG responsive sizing

When changing any SVG component, verify **all three** are present before pushing:

1. `width="100%"` on the `<svg>` element
2. A `viewBox` covering the full coordinate space
3. `style={{ display: 'block', aspectRatio: '<w> / <h>' }}` matching the viewBox dimensions

> **Why**: `width="100%"` + `viewBox` alone falls back to the browser's 150px intrinsic default inside flex or grid containers. The `aspectRatio` style is required to anchor the rendered height.
>
> Also verify that all text inside the SVG uses a **SVG attribute** (`fontSize={N}`) rather than a Tailwind/CSS class (`text-[Npx]`). CSS font-size does not scale with the viewBox.

## Test helpers that mirror domain formulas

When a domain formula is validated (e.g., via a quick script), extract it as a named utility instead of re-implementing it inline in test helpers:

1. Create a small utility (e.g., `frontend/src/test/heatmapUtils.ts`) exporting the formula
2. Import it in test files — never hand-roll a "similar" expression
3. Keep the utility next to the implementation so drift is obvious in code review

> **Why**: Inline re-implementations silently diverge (off-by-one, wrong boundary) and make tests pass for the wrong reason, masking real regressions.

## Exact-value assertions for colours and tier mappings

When testing colour/tier mappings, always assert the **exact expected value**, not "any non-zero value":

```ts
// ✅ Good
expect(cell.getAttribute('fill')).toBe('#f0fdf4');

// ❌ Bad — passes even if the wrong tier is returned
expect(cell.getAttribute('fill')).not.toBe('#1e293b');
```

## Deterministic dates in integration tests

Never use `LocalDate.now()` (or equivalent) in tests that make many sequential DB writes:

- Use a fixed historical date: `logSetsOnDate(fixedDate = LocalDate.of(2025, 6, 1), count = N)`
- Using `now()` across multiple HTTP calls risks straddling UTC midnight in CI

## Rubber-duck review for layout and visual changes

Before pushing a PR that touches SVG, CSS layout, or responsive behaviour, run a rubber-duck review. These areas have invisible browser-rendering edge cases that are cheap to catch early and expensive to fix after a review cycle.
