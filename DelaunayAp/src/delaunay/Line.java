package delaunay;

/*
 * Copyright (c) 2009 by Matt Deckard and Alan Schwartz
 *
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

/*
 * Changelog
 *
 * DATE         AUTHOR          DESCRIPTION
 * 05/01/2009   M. Deckard      Initial creation.
 */

/**
 * Straightforward line implementation.
 *
 * @author Matt Deckard
 * @author Alan Schwartz
 */

public class Line implements Comparable {
    public Pnt a;
    public Pnt b;

    /**
     * Constructor.
     * @param a The first endpoint
     * @param b The second endpoint
     */
    public Line (Pnt a, Pnt b) {
        this.a = a;
        this.b = b;
    }

    public double length () {
        return a.distance(b);
    }

    @Override
    public int compareTo(Object o) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        Line compareLine = (Line) o;
        if (this.length() < compareLine.length()) return BEFORE;
        if (this.length() > compareLine.length()) return AFTER;
        return EQUAL;
    }

    @Override
    public boolean equals (Object o) {
        if (!(o instanceof Line)) return false;
        Line compareLine = (Line) o;
        return ( this.a.equals(compareLine.a) && this.b.equals(compareLine.b) ) ||
               ( this.a.equals(compareLine.b) && this.b.equals(compareLine.a) );
    }

    @Override
    public String toString () {
        return "Line(" + a.toString() + ", " + b.toString();
    }

}
