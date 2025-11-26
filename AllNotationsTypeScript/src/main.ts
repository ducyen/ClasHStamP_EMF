/**
 * Example Repository for
 * Typescript/Node: Setting up Absolute Import Paths 
 * and Live-Reloading Tutorial
 * 
 * Link: https://bgxcode.com/posts/typescript/ts-absolute-import-paths
 */
import 'module-alias/register';
import 'source-map-support/register';
import { ContextImpl } from '@all_notations/java_sample00/model/ContextImpl';

let context = new ContextImpl(0, "Sample Context", 0, 0, 0, undefined, undefined);
context.Start();
context.EventProc(ContextImpl.E1, undefined);
context.EventProc(ContextImpl.E1, undefined);
context.EventProc(ContextImpl.E0, undefined);
context.EventProc(ContextImpl.E5, undefined);
