/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.trolsoft.therat.avr.lexer;

import java.util.logging.Logger;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.LexerInput;
import ru.trolsoft.therat.avr.lexer.data.AvrInstructions;
import ru.trolsoft.therat.avr.lexer.data.AvrRegisters;
import ru.trolsoft.therat.avr.lexer.data.Keywords;


/**
 *
 * @author trol
 */
public class ArtLexer implements Lexer<ArtTokenId> {
	private final LexerRestartInfo<ArtTokenId> info;

	private static final String OPERATORS = "+-=!,.<>;()[]{}|&*/^%:";

	private static final Logger log = Logger.getLogger(ArtLexer.class.getName());


	private static enum State {
		DEFAULT,
		NEW_LINE,
		BLOCK_COMMENT,
		PREPROCESSOR,
	}

	private State state;

    ArtLexer(LexerRestartInfo<ArtTokenId> info) {
        this.info = info;
		  this.state = info.state() == null ? State.DEFAULT : State.values()[(Integer) info.state()];
    }

	@Override
	public Token<ArtTokenId> nextToken() {
		LexerInput input = info.input();
		int ch = input.read();
		if (ch == LexerInput.EOF) {
			return null;
		} else if (isWhitespace(ch)) {
			if (ch == '\r' || ch == '\n') {
				if (state != State.BLOCK_COMMENT) {
					state = State.NEW_LINE;
				}
			}
			do {
				ch = input.read();
				if (ch == '\r' || ch == '\n') {
				if (state != State.BLOCK_COMMENT) {
					state = State.NEW_LINE;
				}
			}
			} while (isWhitespace(ch));
			input.backup(1);
			return create(ArtTokenId.WHITESPACE);
		} else if (ch == ';') {
			do {
				ch = input.read();
			} while (ch != '\n' && ch != '\r' && ch != LexerInput.EOF);
			input.backup(1);
			return create(ArtTokenId.COMMENT);
		} else if (ch == '.') {
			return checkAsmDirective(input);
		} else if (isOperator(ch)) {
			if (ch == '/') {
				ch = input.read();
				if (ch == '*') {
					state = State.BLOCK_COMMENT;
					return create(ArtTokenId.COMMENT_BLOCK);
//				} else if (ch == '/') {
//					do {
//						ch = input.read();
//					} while (ch != '\n' && ch != '\r' && ch != LexerInput.EOF);
//					input.backup(1);
//					return create(ArtTokenId.COMMENT);
				}
				input.backup(1);
			} else if (ch == '*' && state == State.BLOCK_COMMENT) {
				ch = input.read();
				if (ch == '/') {
					state = State.NEW_LINE;
					return create(ArtTokenId.COMMENT_BLOCK);
				} else {
					input.backup(1);
				}
			} else if (ch == '<' && state == State.PREPROCESSOR) {
				int cnt = 0;
				do {
					ch = input.read();
					cnt++;
				} while (ch == '.' || ch == '/' || ch == '\\' || isLetter(ch) || isDigit(ch));
				if (ch == '>') {
					return create(ArtTokenId.STRING);
				}
				input.backup(cnt);
			}
			return create(ArtTokenId.OPERATOR);
		} else if (isDigit(ch)) {
			return checkNumber(input, ch);
		} else if (ch == '\'') {
			return checkCharacter(input);
		} else if (ch == '"') {
			return checkString(input);
		} else if (ch == '@') {
			return checkLocalLabel(input);
		} else if (ch == '#') {
			return checkPreprocessorDirective(input);
		} else if (ch == '$') {
				int cnt = 0;
				do {
					ch = input.read();
					cnt++;
				} while (isHexDigit(ch));
				if (cnt > 1) {
					input.backup(1);
					return create(ArtTokenId.NUMBER_HEX);
				}
				input.backup(cnt);
				return create(ArtTokenId.ERROR);
		} else if (isLetter(ch)) {
			do {
				ch = input.read();
			} while (isLetter(ch) || isDigit(ch));
			if (ch == ':') {
				 if (state == State.NEW_LINE) {
					return create(ArtTokenId.LABEL);
				 } else {
					 return create(ArtTokenId.ARGUMENT);
				 }
			}
			input.backup(1);

			 String str = input.readText().toString();
			 if (Keywords.isKeyword(str)) {
//				 log.info("keyword " + str);
				 return create(ArtTokenId.KEYWORD);
//			 } else if (Keywords.isDirective(str)) {
////				 log.info("directive " + str);
//				 return create(ArtTokenId.ASM_DIRECTIVE);
			 } else if (AvrInstructions.isInstruction(str)) {
				 return create(ArtTokenId.ASM_INSTRUCTION);
			 } else if (AvrRegisters.isRegister(str)) {
				 return create(ArtTokenId.REGISTER);
			 } else if (AvrRegisters.isPair(str)) {
				 return create(ArtTokenId.REGISTER_PAIR);

//			 } else if (Keywords.isFlag(str)) {
//				 return create(ArtTokenId.FLAG);
			 } else if (Keywords.isArray(str)) {
				 return create(ArtTokenId.ARRAY);
			 } else if (Keywords.isDataType(str)) {
				 return create(ArtTokenId.DATATYPE);

			 } else if (Keywords.isBuiltIn(str)) {
				 return create(ArtTokenId.BUILTIN);
			 }
			 return create(ArtTokenId.IDENTIFIER);
//			 log.info("??? " + str);
//		} else {
//			return create(ArtTokenId.IDENTIFIER);
		}

		 return create(ArtTokenId.ERROR);
    }

	private Token<ArtTokenId> checkAsmDirective(LexerInput input) {
		int ch = input.read();
		if (isLetter(ch)) {
			do {
				ch = input.read();
			} while (isLetter(ch) || isDigit(ch));
			input.backup(1);
			String str = input.readText().toString();
//			if (Keywords.isDirective(str)) {
//				return create(ArtTokenId.ASM_DIRECTIVE);
//			}
			input.backup(str.length()-1);
			return create(ArtTokenId.OPERATOR);
		}
		return create(ArtTokenId.ERROR);
	}

	private Token<ArtTokenId> checkCharacter(LexerInput input) {
		int next = input.read();
		int backupCount = 1;
		if (next == LexerInput.EOF) {
			return create(ArtTokenId.ERROR);
		} else if (next == '\'') {
			next = input.read();
			backupCount++;
			if (next == LexerInput.EOF) {
				return create(ArtTokenId.ERROR);
			}
		}
		next = input.read();
		backupCount++;
		switch (next) {
			case LexerInput.EOF:
				return create(ArtTokenId.ERROR);
			case '\'':
				return create(ArtTokenId.CHARACTER);
			default:
				input.backup(backupCount);
				return create(ArtTokenId.ERROR);
		}
	}

	private Token<ArtTokenId> checkString(LexerInput input) {
		int ch = 0;
		do {
			boolean slashed = ch == '\\';
			ch = input.read();
			if (slashed) {
				ch = ' ';
			}
		} while (ch != '\r' && ch != '\n' && ch != LexerInput.EOF && ch != '"');
		if (ch == '"') {
			return create(ArtTokenId.STRING);
		} else {
			input.backup(1);
			return create(ArtTokenId.ERROR);
		}
	}

	private Token<ArtTokenId> checkNumber(LexerInput input, int ch) {
		if (ch == '0') {
			ch = input.read();
			int base;
			if (ch == 'x' || ch == 'X') {
				base = 16;
			} else if (ch == 'b' || ch == 'B') {
				base = 2;
			} else if (isDigit(ch)) {
				base = 8;
			} else if (isLetter(ch)) {
				return create(ArtTokenId.ERROR);
			} else {
				input.backup(1);
				return create(ArtTokenId.NUMBER);
			}
			do {
				ch = input.read();
			} while (isNumberChar(ch, base));
			input.backup(1);
			switch (base) {
				case 2:
					return create(ArtTokenId.NUMBER_BIN);
				case 8:
					return create(ArtTokenId.NUMBER_OCT);
				default:
					return create(ArtTokenId.NUMBER_HEX);
			}
		}
		do {
				ch = input.read();
		} while (isDigit(ch));
		input.backup(1);
		return create(ArtTokenId.NUMBER);
	}

	private Token<ArtTokenId> checkLocalLabel(LexerInput input) {
		int ch;
		do {
			ch = input.read();
		} while (isLetter(ch) || isDigit(ch));
		if (ch == ':') {
			return create(ArtTokenId.LABEL);
		} else {
			input.backup(1);
			return create(ArtTokenId.LABEL);
		}
	}

	private Token<ArtTokenId> checkPreprocessorDirective(LexerInput input) {
		if (state == State.BLOCK_COMMENT) {
			return create(ArtTokenId.COMMENT_BLOCK);
		}
		int ch;
		do {
			ch = input.read();
		} while (isLetter(ch) || isDigit(ch));
		input.backup(1);
		String str = input.readText().toString();
		if (Keywords.isPreprocessor(str)) {
			state = State.PREPROCESSOR;
			return create(ArtTokenId.PREPROCESSOR);
		}
		return create(ArtTokenId.ERROR);
	}


	private static boolean isNumberChar(int ch, int base) {
		switch (base) {
			case 2:
				return ch == '0' || ch == '1';
			case 8:
				return ch >= '0' && ch <= '7';
			case 16:
				return isHexDigit(ch);
		}
		return false;
	}


	 private Token<ArtTokenId> create(ArtTokenId tokenId) {
		 if (state == State.BLOCK_COMMENT) {
			 return info.tokenFactory().createToken(ArtTokenId.COMMENT_BLOCK);
		 } else if (tokenId != ArtTokenId.WHITESPACE) {
			 if (state != State.PREPROCESSOR) {
				state = State.DEFAULT;
			 }
		 }
//if (tokenId == ArtTokenId.ERROR) {
//	log.info("??? " + info.input().readText());
//}
		 return info.tokenFactory().createToken(tokenId);
	 }

    @Override
    public Object state() {
         return state == State.DEFAULT ? null : state.ordinal();
    }

    @Override
    public void release() {
    }

	private static boolean isOperator(int ch) {
		return OPERATORS.indexOf(ch) >= 0;
	}

	private static boolean isDigit(int ch) {
		return ch >= '0' && ch <= '9';
	}

	private static boolean isHexDigit(int ch) {
		return isDigit(ch) || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
	}

	private static boolean isLetter(int ch) {
		return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '_';
	}

	private static boolean isWhitespace(int ch) {
		return ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n';
	}


}
