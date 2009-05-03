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
 * 05/01/2009   M. Deckard      Corrected compareTo function for use in
 *                              search tree
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

    public double orient(Pnt point) {
        return (this.b.getX() * point.getY())
                + (point.getX() * this.a.getY())
                + (this.a.getX() * this.b.getY())
                - (this.a.getY() * this.b.getX())
                - (this.b.getY() * point.getX())
                - (point.getY() * this.a.getX());
    }

    public boolean intersects(Line testLine) {
        // get orients of the endpoints of this line
        double orientA = testLine.orient(this.a);
        double orientB = testLine.orient(this.b);

        // make sure orients are not zero
        if (orientA == 0.0 || orientB == 0.0) return true;

        // see if they're on same side of testLine
        if ( (orientA / orientA) == (orientB / orientB) ) return false;

        // get orients of testLine's endpoints
        orientA = this.orient(testLine.a);
        orientB = this.orient(testLine.b);

        // make sure orients are not zero
        if (orientA == 0.0 || orientB == 0.0) return true;

        // see if they're on same side of testLine
        if ( (orientA / orientA) == (orientB / orientB) ) return false;

        // lines must intersect
        return true;

    }
    
    public boolean cross(Line initial, Line secondary, boolean debug)
    {
        //See if secondary crosses the initial line
        /*
         Found this at: http://www.gidforums.com/t-20866.html
        m1=(y2-y1)/(x2-x1) ---- A
        m2=(v2-v1)/(u2-u1) ---- B

        c1=y1-m1*x1
        c2=v1-m2*x2

        so the intersection points are

        xi=(c2-c1)/(m1-m2) ---- C
        yi=m1*xi+c1

        if((((x1-xi)*(xi-x2))>0)&&(((y1-yi)*(yi-y2))>0)&&(((u1-xi)*(xi-u2))>0)&&(((v1-xi)*(xi-v2))>0))
        {
            both segments intersects each other
        }
        else
        both segments will not intersect each other*/
        
        //System.out.println("A Coord: " + initial.a.getX() + " " + initial.a.getY());
        
        double m1 = (initial.a.getY() - initial.b.getY())/(initial.a.getX() - initial.b.getX());
        double m2 = (secondary.a.getY() - secondary.b.getY())/(secondary.a.getX() - secondary.b.getX());
        
        double c1 = initial.b.getY() - (m1 * initial.b.getX());
        double c2 = secondary.b.getY() - (m2 * initial.a.getX());
        
        double xi = (c2 - c1) / (m1 - m2);
        double yi = (m1 * xi) + c1;
        
        if (((((initial.b.getX() - xi) * (xi - initial.a.getX()) > 0) && ((((initial.b.getY() - yi)*(yi - initial.a.getY()) > 0)
                && (((secondary.b.getX() - xi) * (xi - secondary.a.getX()) > 0) && (secondary.b.getY() - yi) * (yi - secondary.a.getY()) > 0))))))
        {
            return true; //segments intersect each other
        }
        else
            return false;
    }

    @Override
    public int compareTo(Object o) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        Line compareLine = (Line) o;
        if (this.equals(o)) return EQUAL;
        if (this.length() < compareLine.length()) return BEFORE;
        if (this.length() > compareLine.length()) return AFTER;

        // We want to allow different lines of the same length
        // in our sorted line set, so we need to compare by other means if
        // the lengths are equal
        if (this.a.coord(0) < compareLine.a.coord(0)) return BEFORE; //x-coord of first point
        if (this.a.coord(1) < compareLine.a.coord(1)) return BEFORE; //y-coord of first point
        if (this.b.coord(0) < compareLine.b.coord(0)) return BEFORE; //x-coord of second point
        if (this.b.coord(1) < compareLine.b.coord(1)) return BEFORE; //y-coord of second point
        return AFTER;
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
