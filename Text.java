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

import java.util.List;
import reflectionparser.Token.EndOf;

/**
 * Text represents a view into a sequence of Tokens.
 *
 * @author Curtis
 */
public final class Text {

	private final List<Token> data;
	private final int index;

	public Object getUnderlyingSource() {
		return data;
	}

	public Text(List<Token> data) {
		this.data = data;
		index = 0;
	}

	private Text(List<Token> data, int index) {
		this.data = data;
		this.index = index;
	}

	public int size() {
		return data.size() - index;
	}

	public Token first() {
		if (index >= data.size()) {
			return new EndOf(data.get(0).source);
		}
		return data.get(index);
	}

	public Token get(int i) {
		return data.get(index + i);
	}

	public boolean end() {
		return size() == 0;
	}

	public boolean begins(String test) {
		if (size() == 0) {
			return false;
		}
		return first().text.equals(test);
	}

	public boolean begins(List<String> tests) {
		if (tests.size() > size()) {
			return false;
		}
		for (int i = 0; i < tests.size(); ++i) {
			if (!get(i).text.equals(tests.get(i))) {
				return false;
			}
		}
		return true;
	}

	public Text take(int n) {
		return new Text(data, index + n);
	}

	public Text take() {
		return take(1);
	}

	@Override
	public String toString() {
		if (first().text == null) {
			return "EOF";
		}
		return first().text + "...";
	}
}
