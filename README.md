# ClasHStamP\_EMF

**Cl**ass **a**nd **S**tate **H**ierarchical **S**tate **Ma**chine **P**rocessor — built on **Eclipse Modeling Framework (EMF)**

ClasHStamP is a **UML-to-Code Generator**. It reads UML model files created by [Eclipse Papyrus](https://eclipse.dev/papyrus/) and generates source code for multiple programming languages, using CSV-based syntax definition files as templates — fully customizable without touching the generator code itself.

---

## Features

- Reads UML models via Eclipse EMF / UML2 API (`.uml`, `.notation`)
- Generates code for multiple languages: **Java, TypeScript, C++, C, C#**
- Full support for UML notations: class, interface, abstract class, enum, association (aggregation / composition / dependency), inheritance
- Generates **Hierarchical State Machine (HSM)** code from UML State Machine Diagrams, including:
  - Nested states (composite / submachine states)
  - Orthogonal regions
  - Entry / Exit / Do actions
  - Guard conditions, trigger events (CallEvent, SignalEvent, TimeEvent)
  - Pseudostates: initial, choice, join, fork, entry point, exit point
- Code syntax is defined in **CSV files** — easy to extend to new languages
- Reads layout information from `.notation` files to embed each state's bounding rectangle coordinates (used by the simulator)

---

## Project Structure

```
ClasHStamP_EMF/
├── ClasHStamP/          # Core code generator (Eclipse Java project)
│   ├── src/
│   │   ├── stm/         # Main generators
│   │   │   ├── TMain.java           # Entry point — loads UML, traverses classes, calls generators
│   │   │   ├── SyntaxCsv.java       # Reads the CSV syntax definition file
│   │   │   ├── TFileGenerator.java  # Generates file header/footer, handles includes
│   │   │   ├── TClassGenerator.java # Generates class declaration
│   │   │   ├── TNestedClsGenerator.java # Generates nested classes / enums
│   │   │   ├── TOperGenerator.java  # Generates operations (methods)
│   │   │   ├── TAttrGenerator.java  # Generates attributes (fields)
│   │   │   ├── TPropGenerator.java  # Generates properties (getter/setter)
│   │   │   ├── TCtorGenerator.java  # Generates constructors
│   │   │   ├── TBaseGenerator.java  # Base class with shared utilities
│   │   │   └── Utils.java           # String formatting, snake_case, etc.
│   │   └── rfc/
│   │       └── RStmGenerator.java   # Generates UML State Machine (HSM) code
│   ├── release/
│   │   ├── Syntax.xlsb              # Full syntax definition (Excel workbook)
│   │   ├── Syntax_Java.csv          # Syntax for Java
│   │   ├── Syntax_TypeScript.csv    # Syntax for TypeScript
│   │   ├── Syntax_Cpp.csv           # Syntax for C++ source (.cpp)
│   │   ├── Syntax_Hpp.csv           # Syntax for C++ header (.hpp)
│   │   ├── Syntax_CNew.csv          # Syntax for C source (.c)
│   │   ├── Syntax_HNew.csv          # Syntax for C header (.h)
│   │   ├── Syntax_CSharp.csv        # Syntax for C#
│   │   ├── Syntax_JvAbs.csv         # Syntax for Java abstract class
│   │   ├── Syntax_JvIfc.csv         # Syntax for Java interface
│   │   └── ...
│   └── lib/                         # Dependencies (commons-csv, xmlgraphics, etc.)
│
├── AllNotations/        # Sample UML model (Papyrus) — covers all UML notations
│   ├── AllNotations.uml             # UML model file
│   ├── AllNotations.notation        # Diagram layout file
│   └── image/                       # PNG screenshots of diagrams (used by simulator)
│
├── AllNotationsJava/    # Java code generated from AllNotations + Swing simulator
│   └── src/
│       ├── all_notations/java_sample00/
│       │   ├── model/       # Generated code: Context, ContextImpl, AFriend, ...
│       │   ├── base/        # StateMachine.java, EventParams.java
│       │   └── abstracts/   # BaseClass.java, BaseStmTop.java
│       └── simulator/       # Swing GUI: MainWindow, ModelExecutor, RectImageWindow
│
├── AllNotationsC/       # C code generated from AllNotations
│   └── src/AllNotations/JavaSample00/Model/
│
├── AllNotationsTypeScript/  # TypeScript code generated from AllNotations
│   └── src/all_notations/java_sample00/
│
├── AdasUml/             # Sample UML model (ADAS / automotive themed)
└── AdasUmlJava/         # Java code generated from AdasUml + simulator
```

---

## How It Works

### 1. UML Model (Papyrus)

The model is authored in Eclipse Papyrus. Each class in the specified package is generated into a separate source file. Information is read from:

| File | Contents |
|------|----------|
| `.uml` | Model structure: classes, attributes, operations, state machines, transitions... |
| `.notation` | Diagram layout: position and size of each state on the diagram |

### 2. CSV Syntax Definition Files

Each target language has one or more CSV files describing its syntax. Each CSV row is a template for one syntactic construct:

| Column | Meaning |
|--------|---------|
| `item` | Name of the syntactic construct (e.g. `class`, `_ms_attr`, `inheritance`...) |
| `name` | Template for the name / main declaration |
| `ext1st` | Template for the first element |
| `extnxt` | Template for subsequent elements |
| `begin` | Template for the opening of a block |
| `end` | Template for the closing of a block |

The `[->]` marker in CSV templates is replaced with the appropriate indentation at generation time.

### 3. Environment Variables

When running `TMain`, the following environment variables must be provided:

| Variable | Description |
|----------|-------------|
| `PROJECT` | Path to the `.uml` model file |
| `OUTPUT` | Output directory for generated source files |
| `PACKAGE` | Qualified name of the package to generate (e.g. `AllNotations::JavaSample00::Model`) |
| `SYNTAX` | Path to the main syntax CSV file |
| `LANGUAGE` | Target language name (e.g. `Java`, `TypeScript`, `Cpp`...) |
| `SYNTAX_ABSTRACT` | (Optional) Separate CSV syntax for abstract classes |
| `SYNTAX_INTERFACE` | (Optional) Separate CSV syntax for interfaces |
| `SYNTAX_BASECLASS` | (Optional) Separate CSV syntax for base classes |
| `ENCODING` | (Optional) Output file encoding (e.g. `UTF-8`) |
| `NEWLINE_LF` | (Optional) If set, forces `\n` as the line separator |
| `INPUT` | (Optional) Directory containing existing source files (to preserve user-written code) |
| `PATH_DBG` | (Optional) Set to `ON` to embed CSV path debug info in generated output |

### 4. Code Generation Pipeline (per class)

```
TMain (traverses the package)
 └─ TFileGenerator       → file header (imports, package declaration, license comment)
     ├─ TClassGenerator  → class declaration (name, inheritance, modifiers)
     ├─ TNestedClsGenerator → nested classes / enums (EventId, XxxParams...)
     ├─ TOperGenerator   → free-function prototypes & method declarations
     ├─ TAttrGenerator   → static attribute declarations
     ├─ TPropGenerator   → properties (getter/setter)
     ├─ RStmGenerator    → full State Machine code (event enum, state implementations)
     ├─ TCtorGenerator   → constructor
     └─ TFileGenerator   → file footer (close namespace, etc.)
```

---

## State Machine Code Generation

`RStmGenerator` is the most significant component — it generates code for UML Hierarchical State Machines (HSM):

- Each state is encoded as a `long` bitmask constant, enabling hierarchical membership checks via bitwise `AND`
- Generates `Entry`, `Exit`, and `Do` functions for each state and region
- Supports **orthogonal regions** (parallel concurrent regions)
- Supports **submachine states** — sub-state machines defined in separate diagrams
- Reads `.notation` layout data to embed each state's bounding rectangle coordinates into `DefaultEntryAction` / `DefaultExitAction` calls

---

## Simulator (Java)

The generated Java projects include a **Swing-based GUI simulator**:

- **MainWindow**: Main window — a "Start" button instantiates `ContextImpl`, event buttons send events to the state machine
- **RectImageWindow**: Child window displaying a diagram image, with a red rectangle drawn over the currently active state
- **ModelExecutor**: Singleton coordinating rectangle draw/remove operations and managing threads
- **Threading model**:
  - `MainThread` — Swing UI
  - Each state machine `Region` runs in its own thread, allowing orthogonal regions to execute independently

```
MainWindow (MainThread)
 ├── MainStm (Thread 0)
 │    ├── StateX (Thread 0)
 │    └── StateY (Thread 0)
 ├── Region1 (Thread 1)
 │    ├── StateA (Thread 1)
 │    └── StateB (Thread 1)
 └── Region2 (Thread 2)
      ├── StateC (Thread 2)
      └── StateD (Thread 2)
```

When `DefaultEntryAction` is called, a red rectangle appears on the corresponding diagram and remains for 500 ms before the next state in the same region is entered. Other regions are not blocked.

---

## Supported Languages

| Language | CSV Syntax File(s) |
|----------|--------------------|
| Java | `Syntax_Java.csv`, `Syntax_JvAbs.csv`, `Syntax_JvIfc.csv` |
| TypeScript | `Syntax_TypeScript.csv` |
| C++ | `Syntax_Cpp.csv`, `Syntax_Hpp.csv`, `Syntax_HppAbs.csv` |
| C | `Syntax_CNew.csv`, `Syntax_HNew.csv`, `Syntax_CTst.csv` |
| C# | `Syntax_CSharp.csv`, `Syntax_CsAbs.csv`, `Syntax_CsBase.csv`, `Syntax_CsIfc.csv` |

---

## Requirements

- **Eclipse IDE** with the **Papyrus** plugin (for editing UML models)
- **Java 11+** (for running the ClasHStamP generator)
- **Eclipse EMF / UML2 / GMF** runtime libraries (bundled with Eclipse Papyrus)
- **Node.js / npm** (only required for the TypeScript project)

---

## Author

Đức — *"File generated by Đức's ClasHStamP"*
