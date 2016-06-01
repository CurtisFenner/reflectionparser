# reflectionparser
A PEG-like parser for Java classes that uses runtime reflection to make classes parsable.

## A simple example (S-expressions)

The first step is building a way to split the input into tokens to be parsed. Here's just splitting by whitespace.

```java
	String input = "( a ( + 1 2 ) b c ( f x y ) )";
	List<Token> tokens = new ArrayList<>();
	for (String token : input.split("\\s+")) {
		tokens.add(new Token(input, token, 0, 0));
	}
	Text text = new Text(tokens);
```

The next step is defining your grammar.

```java
	@Alternation
	class SExpression {
		public ParenedExpression parened;
		public NumberAtom number;
		public SymbolAtom symbol;
	}
	
	class ParenedExpression {
		@Keyword(keyword = "(", value = 1)
		public String open;
		
		@ZeroOrMore(2)
		public List<SExpression> elements;
		
		@Keyword(keyword = ")", value = 2)
		@Required("expected `)` to close parenthesized expression")
		public String close;
	}
	
	class NumberAtom {
		private final double number;
		private NumberAtom(double number) {
			this.number = number;
		}
	
		public static NumberAtom match(String token) {
			try {
				return new NumberAtom(Double.parseDouble(token));
			} catch (NumberFormatException ex) {
				return null;
			}
		}
	}

	class SymbolAtom {
		private final String symbol;
		private SymbolAtom(String symbol) {
			this.symbol = symbol;
		}
		
		public static SymbolAtom match(String token) {
			if (token.matches("[a-zA-Z-]+")) {
				return new SymbolAtom(token);
			}
			return null;
		}
	}
```

Finally, use the `Mirror` class and process the result:

```java
	try {
		Parse<SExpression> parse = Mirror.parse(SExpression.class, text);
		if (parse == null) {
			throw new ParseException("expected s-expression", text);
		if (!parse.rest.end()) {
			throw new ParseException("expected whole input to be s-expression", parse.rest);
		use(parse.data);
	} catch (ParseException ex) {
		System.out.println("parse exception: " + ex);
	}
```

Now `use` can use Java type-safety to inspect the S-Expression.
