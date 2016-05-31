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

/**
 *
 * @author Curtis
 */
public class Token {

	public final String source;
	public final String text;
	public final int line;
	public final int column;

	public Token(String source, String text, int line, int column) {
		this.source = source;
		this.text = text;
		this.line = line;
		this.column = column;
	}

	public Token append(char c) {
		return new Token(source, text + c, line, column);
	}

	@Override
	public String toString() {
		return "`" + text + "` at line " + line + ", column " + column + " in " + source;
	}

	public static class EndOf extends Token {

		public EndOf(String source) {
			super(source, null, 0, 0);
		}

		@Override
		public String toString() {
			return "end of " + source;
		}
	}
}
