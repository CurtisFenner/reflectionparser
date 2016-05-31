/*
 * The MIT License
 *
 * Copyright 2016 Curtis.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package reflectionparser;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.Pair;

/**
 *
 * @author Curtis
 */
public class Mirror {

	private static final boolean DEBUG = false;

	private static HashMap< Pair<Class, Integer>, Parse> memoize = null;
	private static Object lastSource = null;

	// GRAMMAR /////////////////////////////////////////////////////////////////
	// Produce a representation of the grammar of a single type.
	private static <T> Pair<String, Set<Class>> grammarOne(Class<T> kind) {
		Set<Class> dep = new HashSet<>();
		if (isAlternation(kind)) {
			String result = kind.getSimpleName() + " = ";
			String pad = "";
			while ((pad + "| ").length() < result.length()) {
				pad += " ";
			}
			pad += "| ";
			boolean first = true;
			for (Field field : getAlternationFields(kind)) {
				if (!first) {
					result += "\n" + pad;
				}
				first = false;
				result += field.getType().getSimpleName();
				dep.add(field.getType());
			}
			return new Pair<>(result, dep);
		} else if (isSimple(kind)) {
			return new Pair<>(kind.getSimpleName() + " = ....", dep);
		}
		String result = kind.getSimpleName() + " = ";
		String pad = "";
		while (pad.length() < result.length()) {
			pad += " ";
		}
		boolean first = true;
		for (ParsedField<  ParseFunction<Text, Parse>> field : getSequenceFields(kind)) {
			if (!first) {
				result += "\n" + pad;
			}
			first = false;
			if (field.element != null) {
				result += field.element.getSimpleName() + field.shape;
				dep.add(field.element);
			} else {
				result += field.shape;
			}
		}
		return new Pair<>(result, dep);
	}

	public static <T> String grammar(Class<T> root) {
		Set<Class> seen = new HashSet<>();
		Pair<String, Set<Class>> initial = grammarOne(root);
		String output = initial.left;
		Set<Class> frontier = initial.right;
		while (!frontier.isEmpty()) {
			Class front = frontier.iterator().next();
			frontier.remove(front);
			if (seen.contains(front)) {
				continue;
			}
			seen.add(front);
			Pair<String, Set<Class>> each = grammarOne(front);
			output += "\n\n" + each.left;
			frontier.addAll(each.right);
		}
		return output;
	}

	// HELPERS /////////////////////////////////////////////////////////////////
	private static <T> boolean isAlternation(Class<T> kind) {
		return kind.getAnnotation(Alternation.class) != null;
	}

	private static <T> boolean isSimple(Class<T> kind) {
		try {
			Method match = kind.getMethod("match", String.class);
			return true;
		} catch (NoSuchMethodException ex) {
		} catch (SecurityException ex) {
			Logger.getLogger(Mirror.class.getName()).log(Level.SEVERE, null, ex);
		}
		return false;
	}

	private static List<Field> getAlternationFields(Class kind) {
		ArrayList<Field> fields = new ArrayList<>();
		for (Field field : kind.getFields()) {
			if ((field.getModifiers() & Modifier.PUBLIC) != 0) {
				fields.add(field);
			}
		}
		return fields;
	}

	private static <T> List< ParsedField<  ParseFunction<Text, Parse>>> getSequenceFields(Class<T> kind) {
		ArrayList< ParsedField<  ParseFunction<Text, Parse>>> components = new ArrayList<>();
		for (Field field : kind.getFields()) {
			// Required component
			{
				Component component = field.getDeclaredAnnotation(Component.class);
				if (component != null) {
					components.add(new ParsedField<>(
							field,
							(Text x) -> {
								return parseOne(field.getType(), x);
							},
							component.value(),
							"",
							field.getType()
					));
				}
			}
			{
				// Optional component
				Optional optional = field.getDeclaredAnnotation(Optional.class);
				if (optional != null) {
					components.add(new ParsedField<>(
							field,
							(Text x) -> parseOptional(field.getType(), x),
							optional.value(),
							"?",
							field.getType()
					));
				}
			}
			{
				// OneOrMore component
				OneOrMore oneormore = field.getDeclaredAnnotation(OneOrMore.class);
				if (oneormore != null) {
					ParameterizedType pt = (ParameterizedType) field.getGenericType();
					Class c = (Class<?>) pt.getActualTypeArguments()[0];
					components.add(new ParsedField<>(
							field,
							(Text x) -> parseOneOrMore(c, x),
							oneormore.value(),
							"+",
							c
					));
				}
			}
			{
				// ZeroOrMore component
				ZeroOrMore zeroormore = field.getDeclaredAnnotation(ZeroOrMore.class);
				if (zeroormore != null) {
					ParameterizedType pt = (ParameterizedType) field.getGenericType();
					Class c = (Class<?>) pt.getActualTypeArguments()[0];
					components.add(new ParsedField<>(
							field,
							(Text x) -> parseZeroOrMore(c, x),
							zeroormore.value(),
							"*",
							c
					));
				}
			}
			{
				// Keyword / literal component
				Keyword keyword = field.getDeclaredAnnotation(Keyword.class);
				if (keyword != null) {
					components.add(new ParsedField<>(
							field,
							(Text x) -> parseKeyword(keyword.keyword(), x),
							keyword.value(),
							"`" + keyword.keyword() + "`",
							null
					));
				}
			}
			{
				// Delimited component
				Delimited delimited = field.getDeclaredAnnotation(Delimited.class);
				if (delimited != null) {
					ParameterizedType pt = (ParameterizedType) field.getGenericType();
					Class c = (Class<?>) pt.getActualTypeArguments()[0];
					components.add(new ParsedField<>(
							field,
							(Text x) -> parseDelimited(c, delimited.delimiter(), delimited.trailingAllowed(), delimited.emptyAllowed(), x),
							delimited.value(),
							"~\"" + delimited.delimiter() + "\"",
							c
					));
				}
			}
		}
		Collections.sort(components);
		if (components.isEmpty()) {
			throw new RuntimeException("type `" + kind + "` is not parseable: it does not have any annotated public fields");
		}
		return components;
	}

	////////////////////////////////////////////////////////////////////////////
	private static <T> Parse<List<T>> parseDelimited(Class<T> kind, String delimiter, boolean trailingAllowed, boolean emptyAllowed, Text text) throws ParseException {
		if (DEBUG) {
			System.out.println("<delimited>");
			System.out.println("parseDelimited(" + kind + ", " + text + ")");
		}
		List<T> result = new ArrayList<>();
		while (true) {
			Parse<T> parse = parseOne(kind, text);
			if (parse == null) {
				if (DEBUG) {
					System.out.println("</delimited>");
				}
				if (result.isEmpty() && !emptyAllowed) {
					return null;
				}
				if (!trailingAllowed && !result.isEmpty()) {
					return null;
				}
				return new Parse<>(text, result);
			}
			result.add(parse.data);
			text = parse.rest;
			if (text.begins(delimiter)) {
				text = text.take();
			} else {
				if (DEBUG) {
					System.out.println("</delimited>");
				}
				return new Parse<>(text, result);
			}
		}
	}

	private static Parse<String> parseKeyword(String string, Text text) {
		if (DEBUG) {
			System.out.println("parseKeyword(" + string + ", " + text + ")");
		}
		if (text.begins(string)) {
			return new Parse<>(text.take(), string);
		}
		return null;
	}

	private static <T> Parse<T> parseOptional(Class<T> kind, Text text) throws ParseException {
		if (DEBUG) {
			System.out.println("<optional>");
			System.out.println("parseOptional(" + kind + ", " + text + ")");
		}
		Parse<T> parse = parseOne(kind, text);
		if (DEBUG) {
			System.out.println("</optional>");
		}
		if (parse == null) {
			return new Parse<>(text, null);
		}
		return parse;
	}

	private static <T> Parse<List<T>> parseOneOrMore(Class<T> kind, Text text) throws ParseException {
		if (DEBUG) {
			System.out.println("<one>");
			System.out.println("parseOneOrMore(" + kind + ", " + text + ")");
		}
		List<T> result = new ArrayList<>();
		while (true) {
			Parse<T> parse = parseOne(kind, text);
			if (parse == null) {
				if (DEBUG) {
					System.out.println("</one>");
				}
				if (result.isEmpty()) {
					return null;
				}
				return new Parse<>(text, result);
			}
			result.add(parse.data);
			text = parse.rest;
		}
	}

	private static <T> Parse<List<T>> parseZeroOrMore(Class<T> kind, Text text) throws ParseException {
		if (DEBUG) {
			System.out.println("<zero>");
			System.out.println("parseZeroOrMore(" + kind + ", " + text + ")");
		}
		List<T> result = new ArrayList<>();
		while (true) {
			Parse<T> parse = parseOne(kind, text);
			if (parse == null) {
				if (DEBUG) {
					System.out.println("</zero>");
				}
				return new Parse<>(text, result);
			}
			result.add(parse.data);
			text = parse.rest;
		}
	}

	////////////////////////////////////////////////////////////////////////////
	private static <T> Parse<T> parseAlternation(Class<T> kind, Text text) throws ParseException {
		if (DEBUG) {
			System.out.println("<alternation>");
			System.out.println("parseAlternation(" + kind + ", " + text + ")");
		}
		// Attempt each subfield.
		for (Field field : getAlternationFields(kind)) {
			Parse o = Mirror.parseOne(field.getType(), text);
			if (o != null) {
				try {
					T x = (T) kind.newInstance();
					field.set(x, o.data);
					if (DEBUG) {
						System.out.println("</alternation>");
					}
					return new Parse<>(o.rest, x);
				} catch (InstantiationException | IllegalAccessException ex) {
					throw new IllegalStateException();
				}
			}
		}
		if (DEBUG) {
			System.out.println("</alternation>");
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> Parse<T> parseSequence(Class<T> kind, Text text) throws ParseException {
		if (DEBUG) {
			System.out.println("<sequence>");
			System.out.println("parseSequence(" + kind + ", " + text + ")");
		}

		try {
			final T result = kind.newInstance();
			for (ParsedField<ParseFunction<Text, Parse>> n : getSequenceFields(kind)) {
				final ParseFunction<Text, Parse> parser = n.object;
				Parse parse = parser.apply(text);
				if (parse == null) {
					if (DEBUG) {
						System.out.println("</sequence>");
					}
					Required required = n.field.getDeclaredAnnotation(Required.class);
					if (required != null) {
						throw new ParseException(required.value(), text.first());
					}
					return null;
				}
				n.field.set(result, parse.data);
				text = parse.rest;
			}
			if (DEBUG) {
				System.out.println("</sequence>");
			}
			return new Parse<>(text, result);
		} catch (InstantiationException | IllegalAccessException ex) {
			System.out.println("/!\\ " + kind);
			Logger.getLogger(Mirror.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}

	// Memoize a value
	private static <T> Parse<T> remember(Pair<Class, Integer> index, Parse value) {
		memoize.put(index, value);
		return value;
	}

	@SuppressWarnings("unchecked")
	public static <T> Parse<T> parseOne(Class<T> kind, Text text) throws ParseException {
		if (text.end()) {
			return null;
		}
		// Handle memoization
		if (text.getUnderlyingSource() != lastSource) {
			lastSource = text.getUnderlyingSource();
			memoize = new HashMap<>();
		}
		Pair<Class, Integer> index = new Pair<>(kind, text.size());
		if (memoize.containsKey(index)) {
			return memoize.get(index);
		}

		// Parse alternation
		if (isAlternation(kind)) {
			return remember(index, parseAlternation(kind, text));
		}
		// Parse terminal
		try {
			Method match = kind.getMethod("match", String.class);
			try {
				@SuppressWarnings("unchecked")
				T result = (T) match.invoke(null, text.first().text);
				if (result != null) {
					return remember(index, new Parse<>(text.take(), result));
				}
				return remember(index, null);
			} catch (ClassCastException ex) {
				throw new RuntimeException("Type " + kind + " defines match(String) with wrong return type");
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
				Logger.getLogger(Mirror.class.getName()).log(Level.SEVERE, null, ex);
			}
			return null;
		} catch (NoSuchMethodException ex) {
			// Parse sequence
			return remember(index, parseSequence(kind, text));
		} catch (SecurityException ex) {
			Logger.getLogger(Mirror.class.getName()).log(Level.SEVERE, null, ex);
		}
		throw new IllegalStateException();
	}
}
