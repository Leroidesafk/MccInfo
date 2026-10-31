package leroidesafk.mccinfo.client.items;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class Calculator {
	// Colors of the search bar result
	private static final int COLOR_NUMBER = 0xAAAAAA;
	private static final int COLOR_OPERATOR = 0xFFFFFF;
	private static final int COLOR_RESULT = 0xFFE751;
	private static final int COLOR_ERROR = 0x8C8C8C;
	private static final int[] PAREN_COLORS = {0xFFAA00, 0x55FFFF, 0xFF55FF, 0x55FF55, 0xFF5555, 0x5555FF};
	private static final int MAX_DECIMALS = 6;

	private enum Kind { NUMBER, OPERATOR, OPEN, CLOSE }

	private record Token(Kind kind, String text, String op, double value, int depth) {
	}

	private Calculator() {
	}

	static Component display(String input) {
		// Only when there is a number and an operation
		List<Token> tokens = tokenize(input);
		if (tokens == null || tokens.stream().noneMatch(token -> token.kind() == Kind.NUMBER)
				|| tokens.stream().noneMatch(token -> token.kind() != Kind.NUMBER || token.text().length() > 1 && Character.isLetter(token.text().charAt(token.text().length() - 1)))) {
			return null;
		}
		MutableComponent line = Component.empty();
		Token previous = null;
		for (Token token : tokens) {
			int color = switch (token.kind()) {
				case NUMBER -> COLOR_NUMBER;
				case OPERATOR -> COLOR_OPERATOR;
				case OPEN, CLOSE -> PAREN_COLORS[Math.floorMod(token.depth(), PAREN_COLORS.length)];
			};
			// Spaces around operators between two values
			boolean unary = token.kind() == Kind.OPERATOR && (previous == null || previous.kind() == Kind.OPERATOR || previous.kind() == Kind.OPEN);
			String text = token.kind() == Kind.OPERATOR && !unary ? " " + token.text() + " " : token.text();
			line.append(Component.literal(text).withColor(color));
			previous = token;
		}
		// Result, or ? if it cannot be computed
		Double result = evaluate(tokens);
		if (result != null && Double.isFinite(result)) {
			line.append(Component.literal(" = " + format(result)).withColor(COLOR_RESULT));
		} else {
			line.append(Component.literal(" = ?").withColor(COLOR_ERROR));
		}
		return line;
	}

	private static List<Token> tokenize(String input) {
		List<Token> tokens = new ArrayList<>();
		int depth = 0;
		java.util.ArrayDeque<String> opened = new java.util.ArrayDeque<>();
		int i = 0;
		String text = input.strip();
		while (i < text.length()) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (Character.isDigit(c) || c == '.' || c == ',') {
				// Number, with k, M or B
				int start = i;
				while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.' || text.charAt(i) == ',')) {
					i++;
				}
				String number = text.substring(start, i);
				double multiplier = 1;
				if (i < text.length()) {
					multiplier = switch (Character.toLowerCase(text.charAt(i))) {
						case 'k' -> 1e3;
						case 'm' -> 1e6;
						case 'b' -> 1e9;
						default -> 1;
					};
					if (multiplier != 1) {
						i++;
					}
				}
				try {
					tokens.add(new Token(Kind.NUMBER, text.substring(start, i), "", Double.parseDouble(number.replace(',', '.')) * multiplier, 0));
				} catch (NumberFormatException e) {
					return null;
				}
			} else if (c == 'e' && (i + 1 >= text.length() || !Character.isLetter(text.charAt(i + 1)))) {
				// Constant e
				tokens.add(new Token(Kind.NUMBER, "e", "", Math.E, 0));
				i++;
			} else if (text.startsWith("sqrt(", i)) {
				opened.push("sqrt");
				tokens.add(new Token(Kind.OPEN, "sqrt(", "sqrt", 0, depth++));
				i += 5;
			} else if (c == '(') {
				opened.push("(");
				tokens.add(new Token(Kind.OPEN, "(", "(", 0, depth++));
				i++;
			} else if (c == '|' && "|".equals(opened.peek()) && !tokens.isEmpty()
					&& (tokens.getLast().kind() == Kind.NUMBER || tokens.getLast().kind() == Kind.CLOSE)) {
				// Closing | of an absolute value
				opened.pop();
				depth = Math.max(0, depth - 1);
				tokens.add(new Token(Kind.CLOSE, "|", "|", 0, depth));
				i++;
			} else if (c == '|') {
				opened.push("|");
				tokens.add(new Token(Kind.OPEN, "|", "|", 0, depth++));
				i++;
			} else if (c == ')') {
				if (!opened.isEmpty() && !"|".equals(opened.peek())) {
					opened.pop();
				}
				depth = Math.max(0, depth - 1);
				tokens.add(new Token(Kind.CLOSE, ")", ")", 0, depth));
				i++;
			} else if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
				tokens.add(new Token(Kind.OPERATOR, "**", "^", 0, 0));
				i += 2;
			} else if ("+-*/%^xX×÷".indexOf(c) >= 0) {
				String operator = switch (c) {
					case 'x', 'X', '×' -> "x";
					case '÷' -> "/";
					default -> String.valueOf(c);
				};
				tokens.add(new Token(Kind.OPERATOR, String.valueOf(c), operator, 0, 0));
				i++;
			} else {
				return null;
			}
		}
		return tokens;
	}

	private static Double evaluate(List<Token> tokens) {
		Parser parser = new Parser(tokens);
		try {
			double value = parser.sum();
			return parser.done() ? value : null;
		} catch (IllegalStateException e) {
			return null;
		}
	}

	// Recursive parser: sum, product, unary, power, value
	private static final class Parser {
		private final List<Token> tokens;
		private int index;

		private Parser(List<Token> tokens) {
			this.tokens = tokens;
		}

		private boolean done() {
			return this.index >= this.tokens.size();
		}

		private Token peek() {
			return done() ? null : this.tokens.get(this.index);
		}

		private boolean operator(String text) {
			Token token = peek();
			if (token != null && token.kind() == Kind.OPERATOR && token.op().equals(text)) {
				this.index++;
				return true;
			}
			return false;
		}

		private double sum() {
			double value = product();
			while (true) {
				if (operator("+")) {
					value += product();
				} else if (operator("-")) {
					value -= product();
				} else {
					return value;
				}
			}
		}

		private double product() {
			double value = unary();
			while (true) {
				if (operator("x") || operator("*")) {
					value *= unary();
				} else if (operator("/")) {
					double divisor = unary();
					if (divisor == 0) {
						throw new IllegalStateException();
					}
					value /= divisor;
				} else if (operator("%")) {
					double divisor = unary();
					if (divisor == 0) {
						throw new IllegalStateException();
					}
					value %= divisor;
				} else if (peek() != null && (peek().kind() == Kind.OPEN || peek().kind() == Kind.NUMBER)) {
					// 2(3) and 2e multiply
					value *= unary();
				} else {
					return value;
				}
			}
		}

		private double unary() {
			if (operator("-")) {
				return -unary();
			}
			if (operator("+")) {
				return unary();
			}
			return power();
		}

		private double power() {
			double base = atom();
			if (operator("^")) {
				return Math.pow(base, unary());
			}
			return base;
		}

		private double atom() {
			Token token = peek();
			if (token == null) {
				throw new IllegalStateException();
			}
			this.index++;
			if (token.kind() == Kind.NUMBER) {
				return token.value();
			}
			if (token.kind() == Kind.OPEN) {
				double value = sum();
				Token close = peek();
				boolean abs = token.op().equals("|");
				if (close != null && close.kind() == Kind.CLOSE && close.op().equals(abs ? "|" : ")")) {
					this.index++;
					return abs ? Math.abs(value) : token.op().equals("sqrt") ? Math.sqrt(value) : value;
				}
				throw new IllegalStateException();
			}
			throw new IllegalStateException();
		}
	}

	private static String format(double value) {
		BigDecimal decimal = BigDecimal.valueOf(value).setScale(MAX_DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
		// Very large values keep the exponent
		return Math.abs(value) >= 1e15 ? String.valueOf(value) : decimal.toPlainString();
	}
}
