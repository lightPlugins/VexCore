# VexCore Java style

Write code that can be read without mentally expanding abbreviations or untangling expressions.
These conventions apply to production code, tests, and Java resource-pack tools.

## Layout

- Use four spaces for indentation and continuation lines, never tabs.
- Keep one statement per line and always use braces for conditions and loops.
- Separate methods and logical steps with a blank line. Avoid decorative separators.
- Keep short calls together. Wrap long argument lists with one argument per line and long chains at method calls.
- Aim for 120 characters per line. Do not change a literal's contents just to shorten its source line.
- Keep simple expressions readable; use early returns or a loop when they make the flow clearer.
- Group constants, dependencies, state, constructors, public methods, and private helpers in that order when practical.
- Preserve field initialization order when organizing an existing class.

## Names

- Use meaningful names such as `player`, `inventoryContext`, `slotIndex`, and `cleanupFailure`.
- Avoid abbreviations such as `ctx`, `cfg`, `req`, `tmp`, or single-letter fixture and callback parameters.
- Established terms such as UUID and geometric coordinates x/y/z are acceptable in their usual context.
- Use `var` when the type is obvious at the declaration. Prefer an explicit type for unclear return values.
- Do not rename public methods, record components, serialized fields, or configuration keys during a style-only change.
- Service contracts use responsibility names such as `ScheduleService`; concrete Core implementations
  use `Vex` plus the contract name, such as `VexScheduleService`. Version-specific adapters follow
  the same rule inside their version package. Abstract shared bases may retain the `Abstract` prefix.
- Group services and their supporting classes by feature, with subpackages for distinct responsibilities.
  Keep related types together rather than creating a package for every class. Plugin implementations
  follow their project's prefix (`Arcane` in ArcaneMonolith), not the Core prefix.

## Lombok

- Prefer Lombok whenever it can replace handwritten boilerplate while preserving the existing API
  and behavior. Apply this rule to new code and when editing existing classes.
- Use `@Getter` and `@Setter` for direct field accessors, and `@RequiredArgsConstructor`,
  `@AllArgsConstructor`, or `@NoArgsConstructor` for equivalent assignment-only or empty constructors.
  Prefer field-level annotations when only selected fields should expose accessors.
- Keep explicit methods and constructors that validate or normalize values, copy mutable data,
  synchronize access, perform side effects, or resolve dependencies from `VexServiceRegistry`.
  Do not replace the registry constructor with a constructor accepting individual dependencies.
- Preserve accessor names, visibility, boolean naming, null handling, constructor signatures,
  and serialization behavior. Persistent models retain their required no-argument constructor,
  field names, defaults, and custom setters. Copy constructors remain explicit when they copy state.
- Use focused annotations rather than adding `@Data`, `@Value`, or `@Builder` indiscriminately:
  generated equality, string representations, mutability, and construction APIs must be intentional.
  Keep existing records when they already express the required value semantics.
- Declare Lombok as a compile-only dependency and annotation processor for each applicable source
  set. Keep its version consistent across modules; Lombok is not a runtime dependency.

## Comments

- Describe every service interface and implementation with a short class Javadoc.
- Give every public interface method a concise description of what it does.
- Usually one sentence is enough. Add a second sentence for an important condition, threading rule, or side effect.
- Use `@param`, `@return`, and `@throws` only when they add useful information about the contract.
- Implementations inherit interface method documentation; do not repeat it on every override.
- Add inline comments for non-obvious ordering, concurrency, rollback, persistence, and performance decisions.
- Explain why a step is needed. Avoid comments that merely repeat the next statement.
- Write comments in English and update them whenever the described behavior changes.

## Formatting and verification

IntelliJ reads the project's `.editorconfig`. The matching exported scheme is
`config/intellij-code-style.xml`, which can also be imported into the IDE or passed to its command-line formatter.
Keep the two configurations aligned when changing formatting preferences.

`checkstyleLayout` checks whitespace, braces, and statement layout in main sources and tests.
`checkstyleServices` checks service/registry type comments and their public interface methods.
The existing public API Javadoc checks remain enabled. All these tasks participate in `check`.
Automated checks establish a baseline; reviewers still assess naming and whether comments are useful.

Style changes must preserve public API signatures and runtime behavior. Run the affected tests and
build ArcaneMonolith through its VexCore composite build before completing a broad cleanup.
