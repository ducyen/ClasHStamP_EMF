C:\Workspace\EclipseWsp\jee202509\HelloWorldTS>tsc src\HelloWorld.ts --outDir build

C:\Workspace\EclipseWsp\jee202509\HelloWorldTS>node build\HelloWorld.js
Hello, World




Creating a TypeScript "HelloWorld" program in Eclipse IDE for Enterprise Java and Web Developers requires ensuring that Node.js and a suitable Eclipse plugin are installed and configured. The "Enterprise Java and Web Developers" package often includes basic TypeScript support, but additional tools like the Wild Web Developer or CodeMix plugins are recommended for advanced features. 
Prerequisites
Install Node.js: TypeScript requires Node.js and its package manager (npm) to function outside of a browser environment. Download and install it from the Node.js website.
Install the TypeScript Compiler: Open your system's Command Prompt or Terminal and install the TypeScript compiler globally using npm:
bash
npm install -g typescript
Use code with caution.

Verify the installation by running tsc --version.
Ensure Eclipse Plugins: The "Eclipse IDE for Enterprise Java and Web Developers" package typically includes TypeScript support. If you encounter issues, install the Wild Web Developer plugin from the Eclipse Marketplace:
Go to Help > Eclipse Marketplace.
Search for "Wild Web Developer" and click Install.
Restart Eclipse after installation if prompted. 
Creating the "HelloWorld" Program
Create a New Project:
In Eclipse, go to File > New > Other.
Expand the Web or General category and select the appropriate project type, such as a Static Web Project (you might need to add a Nature, but for a simple TS file, a general project might work too).
Give your project a name (e.g., HelloWorldTS) and click Finish.
Initialize the Project with npm and TypeScript:
Open the Eclipse Terminal view (Window > Show View > Other > Terminal > Terminal) or use an external command prompt navigating to your new project's directory in your workspace.
Initialize a Node.js project: npm init -y. This creates a package.json file.
Generate a TypeScript configuration file: npx tsc --init. This creates a tsconfig.json file, which is essential for defining compiler rules.
Create the TypeScript File:
In the Project Explorer, right-click on your project (or a src folder you create within it) and select New > File.
Name the file HelloWorld.ts.
Add the following code to the editor:
typescript
function sayHello(name: string) {
    return "Hello, " + name;
}

let user = "World";
console.log(sayHello(user));
Use code with caution.

Compile and Run the Program:
You need to compile the .ts file into a .js (JavaScript) file using the TypeScript compiler.
In the Eclipse Terminal or command prompt, run:
bash
tsc HelloWorld.ts
Use code with caution.

This generates a HelloWorld.js file in the same directory.
Run the generated JavaScript file using Node.js:
bash
node HelloWorld.js
Use code with caution.

The output "Hello, World" will be displayed in the console. 
Note: For an automated process, you can configure an external builder in Eclipse to compile your .ts files automatically every time you save them. 