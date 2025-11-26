/**
 * 
 */
export type Integer = number;
export type long = number;
export type Boolean = boolean;
export type String = string;
export interface ResetArgs {
    entryPt?: number;
    lastEnteredStateRecovering?: boolean;
}
export class BaseStmTop {
	
}
export function orAssign( a: boolean, b: boolean ): boolean {
    return a || b;
}
