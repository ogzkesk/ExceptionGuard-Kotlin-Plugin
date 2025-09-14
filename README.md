# ⚡ Exception Guard

**Exception Guard** helps you write safer **Kotlin** code by detecting potential exceptions in your functions, methods, and classes.  

Many apps crash unexpectedly because Kotlin does not enforce checked exceptions (unlike Java). This plugin makes those risks visible directly in the IDE, so you can handle them before they cause runtime errors.

---

## ✨ Features

- 🔍 **Kotlin - Java Function body checking**  
  Detects `throw` statements inside functions and highlights them as warnings.

- 📖 **Doc read**  
  If function KDoc contains `@throws`, a warning will be highlighted.

- 🏷️ **@Throws annotation checking**  
  Functions annotated with `@Throws(...)` will be detected and highlighted.

- ☕ **Java interop throws checking**  
  If a Java function declares `throws`, e.g.  
  ```java
  public void func() throws IllegalArgumentException { ... }

## 🚨 Why this plugin?

Kotlin does not support checked exceptions.
This means developers often miss possible throws in code and documentation.
As a result, many crashes happen silently and unpredictably.

Exception Guard ensures you are aware of every possible exception point in your codebase.

## 📦 Installation

From JetBrains Marketplace

[EXCEPTION GUARD](https://plugins.jetbrains.com/plugin/28476-exception-guard/)

## 🖼️ Screenshots
<p align="center"> <img src="https://raw.githubusercontent.com/ogzkesk/ExceptionGuard/refs/heads/main/throws.jpg" width="600"/> </p>
