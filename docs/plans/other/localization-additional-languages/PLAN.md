# localization-additional-languages — Implementation Plan

Covers: `docs/detailed-spec.md` §10 (Localization File Structure).

**Scope note**: this plan covers only adding the `values-ja/` and `values-zh-rCN/` translated
resource files. It does not cover the UI strings themselves being introduced (each MVP/other
feature's own PLAN.md is responsible for adding its own string resource keys to the default
`values/strings.xml` as that feature is implemented) — this plan's job is translating whatever
keys already exist at the time it's implemented, and keeping the three files in sync afterward.

**Resolution note (default-locale content)**: `docs/detailed-spec.md` §10 explicitly states
`values/strings.xml` (the unqualified/default resource folder) is **English**, not Japanese —
confirmed with `designer-lite` during this planning pass, since an earlier assumption floated
during planning (that the default should be Japanese, given the project's Japan-centric usage
context) turned out not to be backed by any recorded decision and conflicts with the literal,
already-confirmed `detailed-spec.md` §10 wording. No `docs/QA.md` entry was needed — this plan
follows §10 literally: `values/` = default/English, `values-ja/` = Japanese, `values-zh-rCN/` =
Simplified Chinese. There is no separate `values-en/` folder — the unqualified `values/` folder
already serves as the English resource set.

## Index

1. File Layout
2. Translation Workflow
3. Keeping Files in Sync
4. Cross-Feature Touchpoints

## 1. File Layout

```
res/values/strings.xml        # default — English (per docs/detailed-spec.md §10)
res/values-ja/strings.xml     # Japanese
res/values-zh-rCN/strings.xml # Simplified Chinese — zh-rCN folder naming, not Traditional Chinese
```

Android's resource-resolution system automatically picks `values-ja/` on Japanese-locale devices
and `values-zh-rCN/` on Simplified-Chinese-locale devices, falling back to unqualified `values/`
(English) for any other device locale — no in-app language-switching logic is needed; this is
standard `Configuration`-driven resource resolution.

## 2. Translation Workflow

At the point this feature is implemented, enumerate every string resource key already defined
across all MVP and other features' `values/strings.xml` entries (onboarding copy, settings labels,
FGS notification text, permission descriptions, etc.), and produce a matching
`values-ja/strings.xml` and `values-zh-rCN/strings.xml` with the same keys, translated. No new
keys are introduced by this feature — it is a pure translation pass over the existing key set.

## 3. Keeping Files in Sync

Since this feature is implemented after MVP (and potentially after some other deferred features,
depending on implementation order), any string keys added by features implemented *after* this one
will initially only exist in `values/strings.xml` (English) until a follow-up translation pass adds
them to `values-ja/`/`values-zh-rCN/`. This is an accepted, ongoing maintenance task, not a one-time
gap to close — flagged here so it isn't mistaken for an oversight when a later-added feature's
strings aren't yet translated.

## 4. Cross-Feature Touchpoints

- Depends on every other feature's `values/strings.xml` string keys already existing before this
  feature's translation pass — self-contained in mechanism, but its actual translation content is
  necessarily downstream of whichever features have shipped strings by the time this is done.
