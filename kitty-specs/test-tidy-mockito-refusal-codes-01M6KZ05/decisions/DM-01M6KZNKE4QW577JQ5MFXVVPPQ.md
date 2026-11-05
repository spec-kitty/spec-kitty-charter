# Decision Moment `01M6KZNKE4QW577JQ5MFXVVPPQ`

- **Mission:** `test-tidy-mockito-refusal-codes-01M6KZ05`
- **Origin flow:** `plan`
- **Slot key:** `plan.dependencies.starter-test-mockito`
- **Input key:** `starter_test_mockito`
- **Status:** `resolved`
- **Created:** `2026-11-04T19:13:14.180990+00:00`
- **Resolved:** `2026-11-04T19:20:41.477255+00:00`
- **Opened by:** `cli`
- **Other answer:** `true`

## Question

spring-boot-starter-test pulls Mockito transitively into :deployable and :packs tests. Exclude org.mockito from it, or only remove our own declaration and agent?

## Options

- Exclude org.mockito from the starter
- Remove own declaration and agent only
- Other

## Final answer

Keep Mockito: keep our explicit dependency and the Java agent, but align the catalog version to the version the Spring stack provisions (spring-boot-dependencies 4.1.1 manages and spring-boot-starter-test requires 5.23.0; the highest it provisions). Record the reason it is kept (#373 allows recording why).

## Rationale

_(none)_

## Change log

- `2026-11-04T19:13:14.180990+00:00` - opened
- `2026-11-04T19:20:41.477255+00:00` - resolved (final_answer="Keep Mockito: keep our explicit dependency and the Java agent, but align the catalog version to the version the Spring stack provisions (spring-boot-dependencies 4.1.1 manages and spring-boot-starter-test requires 5.23.0; the highest it provisions). Record the reason it is kept (#373 allows recording why).")
