package leroidesafk.mccinfo.client.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// Only support Molang, that's how I'm used to animating
final class Molang {
	// Compiled expression
	interface Expr {
		double eval(Scope scope);
	}

	// Variables and query values
	static final class Scope {
		double animTime;
		double lifeTime;
		final Map<String, Double> variables = new HashMap<>();

		double get(String name) {
			return switch (name) {
				case "query.anim_time", "q.anim_time", "query.life_time", "q.life_time" -> name.endsWith("anim_time") ? this.animTime : this.lifeTime;
				default -> this.variables.getOrDefault(alias(name), 0.0);
			};
		}

		void set(String name, double value) {
			this.variables.put(alias(name), value);
		}

		private static String alias(String name) {
			if (name.startsWith("v.")) {
				return "variable." + name.substring(2);
			}
			if (name.startsWith("t.")) {
				return "temp." + name.substring(2);
			}
			if (name.startsWith("q.")) {
				return "query." + name.substring(2);
			}
			return name;
		}
	}

	// Constants
	static final Expr ZERO = scope -> 0.0;
	static final Expr ONE = scope -> 1.0;

	private final String text;
	private int index;

	private Molang(String text) {
		this.text = text;
	}

	// Number, boolean or expression from JSON
	static Expr of(JsonElement element, double fallback) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return scope -> fallback;
		}
		JsonPrimitive primitive = element.getAsJsonPrimitive();
		if (primitive.isNumber()) {
			double value = primitive.getAsDouble();
			return scope -> value;
		}
		if (primitive.isBoolean()) {
			double value = primitive.getAsBoolean() ? 1.0 : 0.0;
			return scope -> value;
		}
		return parse(primitive.getAsString(), fallback);
	}

	// Statements, assignments and return
	static Expr parse(String source, double fallback) {
		String text = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return scope -> fallback;
		}
		try {
			double value = Double.parseDouble(text);
			return scope -> value;
		} catch (NumberFormatException ignored) {
		}
		List<Expr> statements = new ArrayList<>();
		Expr result = null;
		for (String part : text.split(";")) {
			String statement = part.trim();
			if (statement.isEmpty()) {
				continue;
			}
			boolean returns = statement.startsWith("return ");
			if (returns) {
				statement = statement.substring(7).trim();
			}
			try {
				int assign = assignment(statement);
				if (assign > 0) {
					String name = statement.substring(0, assign).trim();
					Expr value = new Molang(statement.substring(assign + 1)).parseAll();
					statements.add(scope -> {
						double computed = value.eval(scope);
						scope.set(name, computed);
						return computed;
					});
					continue;
				}
				Expr expr = new Molang(statement).parseAll();
				if (returns || result == null) {
					result = expr;
				}
				if (returns) {
					break;
				}
			} catch (RuntimeException e) {
				return scope -> fallback;
			}
		}
		Expr last = result == null ? scope -> fallback : result;
		if (statements.isEmpty()) {
			return last;
		}
		return scope -> {
			for (Expr statement : statements) {
				statement.eval(scope);
			}
			return last.eval(scope);
		};
	}

	private static int assignment(String statement) {
		for (int i = 0; i < statement.length(); i++) {
			char c = statement.charAt(i);
			if (c == '=') {
				char next = i + 1 < statement.length() ? statement.charAt(i + 1) : ' ';
				char previous = i > 0 ? statement.charAt(i - 1) : ' ';
				return next != '=' && previous != '=' && previous != '!' && previous != '<' && previous != '>' ? i : -1;
			}
			if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != ' ') {
				return -1;
			}
		}
		return -1;
	}

	// Parser, lowest priority first
	private Expr parseAll() {
		Expr expr = ternary();
		skip();
		if (this.index < this.text.length()) {
			throw new IllegalArgumentException("Unexpected " + this.text.charAt(this.index));
		}
		return expr;
	}

	private void skip() {
		while (this.index < this.text.length() && Character.isWhitespace(this.text.charAt(this.index))) {
			this.index++;
		}
	}

	private boolean eat(String token) {
		skip();
		if (this.text.startsWith(token, this.index)) {
			this.index += token.length();
			return true;
		}
		return false;
	}

	private Expr ternary() {
		Expr condition = or();
		if (eat("??")) {
			Expr other = ternary();
			return scope -> {
				double value = condition.eval(scope);
				return value != 0.0 ? value : other.eval(scope);
			};
		}
		if (eat("?")) {
			Expr yes = ternary();
			Expr no = eat(":") ? ternary() : ZERO;
			return scope -> condition.eval(scope) != 0.0 ? yes.eval(scope) : no.eval(scope);
		}
		return condition;
	}

	private Expr or() {
		Expr left = and();
		while (eat("||")) {
			Expr a = left;
			Expr b = and();
			left = scope -> a.eval(scope) != 0.0 || b.eval(scope) != 0.0 ? 1.0 : 0.0;
		}
		return left;
	}

	private Expr and() {
		Expr left = comparison();
		while (eat("&&")) {
			Expr a = left;
			Expr b = comparison();
			left = scope -> a.eval(scope) != 0.0 && b.eval(scope) != 0.0 ? 1.0 : 0.0;
		}
		return left;
	}

	private Expr comparison() {
		Expr left = additive();
		while (true) {
			Expr a = left;
			if (eat("==")) {
				Expr b = additive();
				left = scope -> a.eval(scope) == b.eval(scope) ? 1.0 : 0.0;
			} else if (eat("!=")) {
				Expr b = additive();
				left = scope -> a.eval(scope) != b.eval(scope) ? 1.0 : 0.0;
			} else if (eat("<=")) {
				Expr b = additive();
				left = scope -> a.eval(scope) <= b.eval(scope) ? 1.0 : 0.0;
			} else if (eat(">=")) {
				Expr b = additive();
				left = scope -> a.eval(scope) >= b.eval(scope) ? 1.0 : 0.0;
			} else if (eat("<")) {
				Expr b = additive();
				left = scope -> a.eval(scope) < b.eval(scope) ? 1.0 : 0.0;
			} else if (eat(">")) {
				Expr b = additive();
				left = scope -> a.eval(scope) > b.eval(scope) ? 1.0 : 0.0;
			} else {
				return left;
			}
		}
	}

	private Expr additive() {
		Expr left = multiplicative();
		while (true) {
			Expr a = left;
			if (eat("+")) {
				Expr b = multiplicative();
				left = scope -> a.eval(scope) + b.eval(scope);
			} else if (eat("-")) {
				Expr b = multiplicative();
				left = scope -> a.eval(scope) - b.eval(scope);
			} else {
				return left;
			}
		}
	}

	private Expr multiplicative() {
		Expr left = unary();
		while (true) {
			Expr a = left;
			if (eat("*")) {
				Expr b = unary();
				left = scope -> a.eval(scope) * b.eval(scope);
			} else if (eat("/")) {
				Expr b = unary();
				left = scope -> {
					double divisor = b.eval(scope);
					return divisor == 0.0 ? 0.0 : a.eval(scope) / divisor;
				};
			} else {
				return left;
			}
		}
	}

	private Expr unary() {
		if (eat("-")) {
			Expr value = unary();
			return scope -> -value.eval(scope);
		}
		if (eat("+")) {
			return unary();
		}
		if (eat("!")) {
			Expr value = unary();
			return scope -> value.eval(scope) == 0.0 ? 1.0 : 0.0;
		}
		return primary();
	}

	private Expr primary() {
		skip();
		if (eat("(")) {
			Expr inner = ternary();
			eat(")");
			return inner;
		}
		int start = this.index;
		if (this.index < this.text.length() && (Character.isDigit(this.text.charAt(this.index)) || this.text.charAt(this.index) == '.')) {
			while (this.index < this.text.length() && (Character.isDigit(this.text.charAt(this.index)) || this.text.charAt(this.index) == '.')) {
				this.index++;
			}
			double value = Double.parseDouble(this.text.substring(start, this.index));
			if (this.index < this.text.length() && this.text.charAt(this.index) == 'f') {
				this.index++;
			}
			return scope -> value;
		}
		while (this.index < this.text.length() && (Character.isLetterOrDigit(this.text.charAt(this.index)) || this.text.charAt(this.index) == '_'
				|| this.text.charAt(this.index) == '.')) {
			this.index++;
		}
		String name = this.text.substring(start, this.index);
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Expected a value");
		}
		List<Expr> args = new ArrayList<>();
		if (eat("(")) {
			if (!eat(")")) {
				do {
					args.add(ternary());
				} while (eat(","));
				eat(")");
			}
		}
		return identifier(name, args);
	}

	// Variables and math functions, angles in degrees
	private static Expr identifier(String name, List<Expr> args) {
		String function = name.startsWith("math.") ? name.substring(5) : null;
		if (function == null) {
			return switch (name) {
				case "true" -> ONE;
				case "false" -> ZERO;
				default -> scope -> scope.get(name);
			};
		}
		Expr a = args.isEmpty() ? ZERO : args.get(0);
		Expr b = args.size() < 2 ? ZERO : args.get(1);
		Expr c = args.size() < 3 ? ZERO : args.get(2);
		return switch (function) {
			case "pi" -> scope -> Math.PI;
			case "abs" -> scope -> Math.abs(a.eval(scope));
			case "sin" -> scope -> Math.sin(Math.toRadians(a.eval(scope)));
			case "cos" -> scope -> Math.cos(Math.toRadians(a.eval(scope)));
			case "asin" -> scope -> Math.toDegrees(Math.asin(a.eval(scope)));
			case "acos" -> scope -> Math.toDegrees(Math.acos(a.eval(scope)));
			case "atan" -> scope -> Math.toDegrees(Math.atan(a.eval(scope)));
			case "atan2" -> scope -> Math.toDegrees(Math.atan2(a.eval(scope), b.eval(scope)));
			case "sqrt" -> scope -> Math.sqrt(a.eval(scope));
			case "pow" -> scope -> Math.pow(a.eval(scope), b.eval(scope));
			case "exp" -> scope -> Math.exp(a.eval(scope));
			case "ln" -> scope -> Math.log(a.eval(scope));
			case "floor" -> scope -> Math.floor(a.eval(scope));
			case "ceil" -> scope -> Math.ceil(a.eval(scope));
			case "round" -> scope -> Math.round(a.eval(scope));
			case "trunc" -> scope -> (double) (long) a.eval(scope);
			case "mod" -> scope -> {
				double divisor = b.eval(scope);
				return divisor == 0.0 ? 0.0 : a.eval(scope) % divisor;
			};
			case "min" -> scope -> Math.min(a.eval(scope), b.eval(scope));
			case "max" -> scope -> Math.max(a.eval(scope), b.eval(scope));
			case "clamp" -> scope -> Math.clamp(a.eval(scope), b.eval(scope), c.eval(scope));
			case "lerp" -> scope -> {
				double from = a.eval(scope);
				return from + (b.eval(scope) - from) * c.eval(scope);
			};
			case "lerprotate" -> scope -> {
				double from = a.eval(scope);
				double delta = ((b.eval(scope) - from) % 360.0 + 540.0) % 360.0 - 180.0;
				return from + delta * c.eval(scope);
			};
			case "random" -> scope -> {
				double low = a.eval(scope);
				return low + ThreadLocalRandom.current().nextDouble() * (b.eval(scope) - low);
			};
			case "random_integer" -> scope -> {
				long low = Math.round(a.eval(scope));
				long high = Math.round(b.eval(scope));
				return high <= low ? low : low + ThreadLocalRandom.current().nextLong(high - low + 1);
			};
			case "die_roll" -> scope -> {
				double sum = 0.0;
				long count = Math.round(a.eval(scope));
				double low = b.eval(scope);
				double high = c.eval(scope);
				for (long i = 0; i < count; i++) {
					sum += low + ThreadLocalRandom.current().nextDouble() * (high - low);
				}
				return sum;
			};
			default -> ZERO;
		};
	}
}
