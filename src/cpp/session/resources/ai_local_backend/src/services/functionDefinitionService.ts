/*---------------------------------------------------------------------------------------------
 * Copyright (c) Lotas Inc. All rights reserved.
 * Licensed under the MIT License.
 *--------------------------------------------------------------------------------------------*/

import * as path from 'path';
import * as fs from 'fs';
import { IFunctionDefinitionService } from '../types';
import { DEVELOPER_INSTRUCTIONS } from '../config/developerInstructions';
import { functionsConfig } from '../config/functions';

interface FunctionDefinitionMap {
	[functionName: string]: any;
}

export class FunctionDefinitionService implements IFunctionDefinitionService {

	private functionMap: FunctionDefinitionMap = {};

	constructor() {
		this.loadFunctions();
	}

	private loadFunctions(): void {
		try {
			// Import the functions config from TypeScript module - gets compiled and bundled
			const functions = functionsConfig.functions;

			if (functions && Array.isArray(functions)) {
				for (const func of functions) {
					const functionName = func.name;
					this.functionMap[functionName] = func;
				}
			}

		} catch (error) {
			throw new Error(`Failed to load function definitions - ${error instanceof Error ? error.message : String(error)}`);
		}
	}

	/**
	 * Get specific functions by names
	 */
	public getFunctionsByNames(functionNames: string[]): any[] {
		const tools: any[] = [];
		for (const functionName of functionNames) {
			const func = this.functionMap[functionName];
			if (func) {
				tools.push(func);
			}
		}
		return tools;
	}

	/**
	 * Load developer instructions
	 */
	public async loadDeveloperInstructions(model: string): Promise<string> {
		let instructions = DEVELOPER_INSTRUCTIONS;

		// Add Anthropic-specific tool usage instructions
		if (model && model.startsWith('claude-')) {
			instructions += '\n\nAnswer the user\'s request using relevant tools (if they are available). Before calling a tool, do some analysis within <thinking></thinking> tags. First, think about which of the provided tools is the relevant tool to answer the user\'s request. Second, go through each of the required parameters of the relevant tool and determine if the user has directly provided or given enough information to infer a value. When deciding if the parameter can be inferred, carefully consider all the context to see if it supports a specific value. If all of the required parameters are present or can be reasonably inferred, close the thinking tag and proceed with the tool call. BUT, if one of the values for a required parameter is missing, DO NOT invoke the function (not even with fillers for the missing params) and instead, ask the user to provide the missing parameters. DO NOT ask for more information on optional parameters if it is not provided.';
		}

		return instructions;
	}
}

