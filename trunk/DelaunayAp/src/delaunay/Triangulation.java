package delaunay;

/*
 * Copyright (c) 2005, 2007 by L. Paul Chew.
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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import jpaul.DataStructs.DisjointSet;

/**
 * A 2D Delaunay Triangulation (DT) with incremental site insertion.
 *
 * This is not the fastest way to build a DT, but it's a reasonable way to build
 * a DT incrementally and it makes a nice interactive display. There are several
 * O(n log n) methods, but they require that the sites are all known initially.
 *
 * A Triangulation is a Set of Triangles. A Triangulation is unmodifiable as a
 * Set; the only way to change it is to add sites (via delaunayPlace).
 *
 * @author Paul Chew
 *
 * Created July 2005. Derived from an earlier, messier version.
 *
 * Modified November 2007. Rewrote to use AbstractSet as parent class and to use
 * the Graph class internally. Tried to make the DT algorithm clearer by
 * explicitly creating a cavity.  Added code needed to find a Voronoi cell.
 *
 */

/*
 * Changelog
 *
 * DATE         AUTHOR          DESCRIPTION
 * 04/05/2009   M. Deckard      Added support for Gabriel graphs
 * 04/11/2009   M. Deckard      Added support for RNGs
 * 05/01/2009   M. Deckard      Added support for EMSTs
 * 05/01/2009   M. Deckard      Added sanity check to EMST algorithm
 * 05/02/2009   M. Deckard      Added support for removing points and finding
 *                              nearest point to a query location
 */

public class Triangulation extends AbstractSet<Triangle> {

    private boolean ggDebug = false;        // Debug output for Gabriel graphs
    private boolean rngDebug = false;       // Debug output for RNGs
    private boolean emstDebug = false;      // Debug output for EMSTs
    private boolean mwtDebug = false;       // Debug output for MWTs
    private Triangle mostRecent = null;     // Most recently "active" triangle
    private Triangle initTri;               // Initial "bounding" triangle
    private Graph<Triangle> triGraph;       // Holds triangles for navigation
    private Set<Line> emstLineSet;          // Holds candidate lines for EMST
    private Set<Pnt> pointSet;              // Holds all points in graph
    private Graph<Pnt> gabrielGraph;        // Holds points in Gabriel graph
    private Graph<Pnt> rnGraph;             // Holds points in RNG
    private Graph<Pnt> emstGraph;           // Holds points in Euclidean MST
    private Graph<Pnt> mwtGraph;            // Holds points in Euclidean MST
    private Set<Line> mwtLineSet;           // Holds candidate lines for EMST

    /**
     * All sites must fall within the initial triangle.
     * @param triangle the initial triangle
     */
    public Triangulation (Triangle triangle) {
        triGraph = new Graph<Triangle>();
        initTri = triangle;
        Pnt point0 = initTri.get(0);
        Pnt point1 = initTri.get(1);
        Pnt point2 = initTri.get(2);
        triGraph.add(initTri);
        mostRecent = initTri;

        // Line set init
        emstLineSet = new TreeSet<Line>();
        emstLineSet.add(new Line(point0, point1));
        emstLineSet.add(new Line(point1, point2));
        emstLineSet.add(new Line(point2, point0));

        // Point set init
        pointSet = new HashSet<Pnt>();
        pointSet.add(point0);
        pointSet.add(point1);
        pointSet.add(point2);

        // Gabriel init
        gabrielGraph = new Graph<Pnt>();
        for (Pnt vertex: initTri) {
            gabrielGraph.add(vertex);
            for (Pnt vertex2: initTri) {
                gabrielGraph.add(vertex2);
                gabrielGraph.add(vertex, vertex2);
            }
        }

        // RNG init
        rnGraph = new Graph<Pnt>();
        for (Pnt vertex: initTri) {
            rnGraph.add(vertex);
            for (Pnt vertex2: initTri) {
                rnGraph.add(vertex2);
                rnGraph.add(vertex, vertex2);
            }
        }

        // EMST init
        emstGraph = new Graph<Pnt>();
    }

    /* The following two methods are required by AbstractSet */

    @Override
    public Iterator<Triangle> iterator () {
        return triGraph.nodeSet().iterator();
    }

    @Override
    public int size () {
        return triGraph.nodeSet().size();
    }

    @Override
    public String toString () {
        return "Triangulation with " + size() + " triangles";
    }

    /**
     * True iff triangle is a member of this triangulation.
     * This method isn't required by AbstractSet, but it improves efficiency.
     * @param triangle the object to check for membership
     */
    public boolean contains (Object triangle) {
        return triGraph.nodeSet().contains(triangle);
    }

    /**
     * Report neighbor opposite the given vertex of triangle.
     * @param site a vertex of triangle
     * @param triangle we want the neighbor of this triangle
     * @return the neighbor opposite site in triangle; null if none
     * @throws IllegalArgumentException if site is not in this triangle
     */
    public Triangle neighborOpposite (Pnt site, Triangle triangle) {
        if (!triangle.contains(site))
            throw new IllegalArgumentException("Bad vertex; not in triangle");
        for (Triangle neighbor: triGraph.neighbors(triangle)) {
            if (!neighbor.contains(site)) return neighbor;
        }
        return null;
    }

    /**
     * Return the set of triangles adjacent to triangle.
     * @param triangle the triangle to check
     * @return the neighbors of triangle
     */
    public Set<Triangle> neighbors(Triangle triangle) {
        return triGraph.neighbors(triangle);
    }

    /**
     * Report triangles surrounding site in order (cw or ccw).
     * @param site we want the surrounding triangles for this site
     * @param triangle a "starting" triangle that has site as a vertex
     * @return all triangles surrounding site in order (cw or ccw)
     * @throws IllegalArgumentException if site is not in triangle
     */
    public List<Triangle> surroundingTriangles (Pnt site, Triangle triangle) {
        if (!triangle.contains(site))
            throw new IllegalArgumentException("Site not in triangle");
        List<Triangle> list = new ArrayList<Triangle>();
        Triangle start = triangle;
        Pnt guide = triangle.getVertexButNot(site);        // Affects cw or ccw
        while (true) {
            list.add(triangle);
            Triangle previous = triangle;
            triangle = this.neighborOpposite(guide, triangle); // Next triangle
            guide = previous.getVertexButNot(site, guide);     // Update guide
            if (triangle == start) break;
        }
        return list;
    }

    /**
     * Returns true iff the Gabriel graph has an edge between site1 and site2
     * @param site1 first point
     * @param site2 second point
     */
    public boolean gabrielEdge (Pnt site1, Pnt site2) {
        return gabrielGraph.areNeighbors(site1, site2);
    }

    /**
     * Returns true iff the RNG has an edge between site1 and site2
     * @param site1 first point
     * @param site2 second point
     */
    public boolean rngEdge (Pnt site1, Pnt site2) {
        return rnGraph.areNeighbors(site1, site2);
    }

    /**
     * Returns true iff the EMST has an edge between site1 and site2
     * @param site1 first point
     * @param site2 second point
     */
    public boolean emstEdge (Pnt site1, Pnt site2) {
        return emstGraph.contains(site1, site2);
    }
    
    public boolean mwtEdge (Pnt site1, Pnt site2) {
        return mwtGraph.contains(site1, site2);
    }

    /**
     * Locate the triangle with point inside it or on its boundary.
     * @param point the point to locate
     * @return the triangle that holds point; null if no such triangle
     */
    public Triangle locate (Pnt point) {
        Triangle triangle = mostRecent;
        if (!this.contains(triangle)) triangle = null;

        // Try a directed walk (this works fine in 2D, but can fail in 3D)
        Set<Triangle> visited = new HashSet<Triangle>();
        while (triangle != null) {
            if (visited.contains(triangle)) { // This should never happen
                System.out.println("Warning: Caught in a locate loop");
                break;
            }
            visited.add(triangle);
            // Corner opposite point
            Pnt corner = point.isOutside(triangle.toArray(new Pnt[0]));
            if (corner == null) return triangle;
            triangle = this.neighborOpposite(corner, triangle);
        }
        // No luck; try brute force
        System.out.println("Warning: Checking all triangles for " + point);
        for (Triangle tri: this) {
            if (point.isOutside(tri.toArray(new Pnt[0])) == null) return tri;
        }
        // No such triangle
        System.out.println("Warning: No triangle holds " + point);
        return null;
    }

    /**
     * Place a new site into the DT.
     * Nothing happens if the site matches an existing DT vertex.
     * @param site the new Pnt
     * @throws IllegalArgumentException if site does not lie in any triangle
     */
    public void delaunayPlace (Pnt site) {
        // Uses straightforward scheme rather than best asymptotic time

        // Locate containing triangle
        Triangle triangle = locate(site);
        // Give up if no containing triangle or if site is already in DT
        if (triangle == null)
            throw new IllegalArgumentException("No containing triangle");
        if (triangle.contains(site)) return;

        // Determine the cavity and update the triangulation
        Set<Triangle> cavity = getCavity(site, triangle);
        mostRecent = update(site, cavity);
        
        // Update EMST
        computeEMST();
        computeMWT();
    }

    /**
     * Find nearest point to a location
     * @param query the query location
     * @return point closest to query
     */
    public Pnt findNearest (Pnt query) {
        Triangle containing = locate(query);
        Pnt result = initTri.getVertexButNot();
        double minDist = result.distance(query);
        for (Pnt point : containing) {
            double newDist = point.distance(query);
            if (newDist < minDist) {
                minDist = newDist;
                result = point;
            }
        }
        return result;
    }

    /**
     * Removes a site from the DT.
     * Nothing happens if the site doesn't match an existing DT vertex.
     * @param site the new Pnt
     * @throws IllegalArgumentException if site does not lie in any triangle
     */
    public void delaunayRemove (Pnt site) {
        // Return if site isn't in the graph
        if ( ! pointSet.contains(site) ) {
            System.out.println("ERROR: tried to remove point that wasn't there: " + site.toString());
            return;
        }

        // Remove site from pointSet
        pointSet.remove(site);

        // Save array of points we want to keep
        Pnt[] keepPoints = pointSet.toArray(new Pnt[pointSet.size()]);

        // Reinitialize this triangulation
        clear();
        for (Pnt point : keepPoints) {
            delaunayPlace(point);
        }
    }

    /**
     * Clears this triangulation.
     * Uses the same initial "bounding" triangulation. All other points
     * and graphs are cleared.
     */
    @Override
    public void clear() {
        //private Set<Pnt> pointSet;               // Holds all points in graph
        //private Graph<Pnt> gabrielGraph;         // Holds points in Gabriel graph
        //private Graph<Pnt> rnGraph;              // Holds points in RNG
        //private Graph<Pnt> emstGraph;            // Holds points in Euclidean MST

        //super.clear(); // Clear Triangulation

        // Reinit triGraph, mostRecent
        triGraph = new Graph<Triangle>();
        Pnt point0 = initTri.get(0);
        Pnt point1 = initTri.get(1);
        Pnt point2 = initTri.get(2);
        triGraph.add(initTri);
        mostRecent = initTri;

        // Line set reinit
        emstLineSet = new TreeSet<Line>();
        emstLineSet.add(new Line(point0, point1));
        emstLineSet.add(new Line(point1, point2));
        emstLineSet.add(new Line(point2, point0));

        // Point set reinit
        pointSet = new HashSet<Pnt>();
        pointSet.add(point0);
        pointSet.add(point1);
        pointSet.add(point2);

        // Gabriel reinit
        gabrielGraph = new Graph<Pnt>();
        for (Pnt vertex: initTri) {
            gabrielGraph.add(vertex);
            for (Pnt vertex2: initTri) {
                gabrielGraph.add(vertex2);
                gabrielGraph.add(vertex, vertex2);
            }
        }

        // RNG reinit
        rnGraph = new Graph<Pnt>();
        for (Pnt vertex: initTri) {
            rnGraph.add(vertex);
            for (Pnt vertex2: initTri) {
                rnGraph.add(vertex2);
                rnGraph.add(vertex, vertex2);
            }
        }

        // EMST reinit
        emstGraph = new Graph<Pnt>();

    }

    /**
     * Computes the Euclidean minimum spanning tree.
     */
    public void computeEMST () {
        // Reinit EMST graph
        emstGraph = new Graph<Pnt>();
        for(Pnt point: pointSet) {
            emstGraph.add(point);
        }

        // The disjoint set
        DisjointSet<Pnt> ds = new DisjointSet<Pnt>();

        // The sorted array of lines
        Line[] lineArray = emstLineSet.toArray(new Line[emstLineSet.size()]);

        int count = 0;
        if (emstDebug) System.out.println("EMST: need " + (pointSet.size() - 1) + " lines");
        if (emstDebug) System.out.println("EMST: there are " + emstLineSet.size() + " lines in RNG");
        for (int i = 0; count < pointSet.size() - 1 && i < emstLineSet.size(); i++){
            if (emstDebug) System.out.println("EMST: considering " + lineArray[i].toString());
            if ( ! ds.find(lineArray[i].a).equals(ds.find(lineArray[i].b)) ) {
                count++;
                if (emstDebug) System.out.println("EMST: adding " + count + "th edge");
                ds.union(ds.find(lineArray[i].a), ds.find(lineArray[i].b));
                emstGraph.add(lineArray[i].a, lineArray[i].b);
            }
        }
        if (count < pointSet.size() - 1) // This should never happen!
            System.out.println("EMST: WARNING! Missing " + (pointSet.size() - 1 - count) + " points!");
    }

    public void computeMWT () {
        // Reinit MWT graph
        Set<Pnt> mwtPointSet = new HashSet<Pnt>();
        
        //Not sure why these points are in Pointset - removing them
        Pnt badpoint = new Pnt(10000, -10000);
        Pnt badpoint1 = new Pnt(-10000, -10000);
        Pnt badpoint2 = new Pnt(0, 10000);
        
        //Get all points
        mwtGraph = new Graph<Pnt>();
        for(Pnt point: pointSet) {
            if (!point.equals(badpoint) && !point.equals(badpoint1) && !point.equals(badpoint2))
            {
            mwtPointSet.add(point);
            //System.out.println(point + " point for MWT");
            }
        }
        
        //Get all possible lines
        int i = 0;
        int j = 0;
        mwtLineSet = new TreeSet<Line>();
        for (Pnt point0: mwtPointSet)
        {
            j = 0;
            for (Pnt point1: mwtPointSet){
                if (i > j)
                {
                    mwtLineSet.add(new Line(point0, point1));
                }
                j++;
            }
            i++;
        }
        
        //Show all possible edges
        Line[] lineArray = mwtLineSet.toArray(new Line[mwtLineSet.size()]);
        for(Line line: lineArray) {
            //mwtGraph.add(point);
            if (mwtDebug) System.out.println(line + " possible line for MWT");
        }
        
        //Print permutations of these edgelists
        

        //Add edges without overlapping
        Set<Line> finishedMWTLineSet = new TreeSet<Line>();
        Line[] finishedLineArray;// = mwtLineSet.toArray(new Line[mwtLineSet.size()]); 
        boolean keepline = false;
        
        for (Line line: lineArray)
        {
            keepline = true;
            finishedLineArray = finishedMWTLineSet.toArray(new Line[finishedMWTLineSet.size()]);
            for (Line finishedline: finishedLineArray) //THIS IS NOT WORKING YET
            {
                if (line.cross(line, finishedline, mwtDebug))
                    keepline = false;
            }
            if (keepline == true)
            {
               finishedMWTLineSet.add(line);
               if (mwtDebug) System.out.println("Adding a line");
            }
        }
        
        //Print all the final edges
        finishedLineArray = finishedMWTLineSet.toArray(new Line[finishedMWTLineSet.size()]);
        for(Line line: finishedLineArray) {
            //mwtGraph.add(point);
            if (mwtDebug) System.out.println(line + " FINAL line for MWT");
        }
        
        // The disjoint set
        //DisjointSet<Pnt> ds = new DisjointSet<Pnt>();

        // The sorted array of lines
       /* Line[] lineArray = emstLineSet.toArray(new Line[emstLineSet.size()]);

        int count = 0;
        if (emstDebug) System.out.println("EMST: need " + (pointSet.size() - 1) + " lines");
        if (emstDebug) System.out.println("EMST: there are " + emstLineSet.size() + " lines in RNG");
        for (int i = 0; count < pointSet.size() - 1 && i < emstLineSet.size(); i++){
            if (emstDebug) System.out.println("EMST: considering " + lineArray[i].toString());
            if ( ! ds.find(lineArray[i].a).equals(ds.find(lineArray[i].b)) ) {
                count++;
                if (emstDebug) System.out.println("EMST: adding " + count + "th edge");
                ds.union(ds.find(lineArray[i].a), ds.find(lineArray[i].b));
                emstGraph.add(lineArray[i].a, lineArray[i].b);
            }
        }
        if (count < pointSet.size() - 1) // This should never happen!
            System.out.println("EMST: WARNING! Missing " + (pointSet.size() - 1 - count) + " points!");*/
    }
    
    /**
     * Determine the cavity caused by site.
     * @param site the site causing the cavity
     * @param triangle the triangle containing site
     * @return set of all triangles that have site in their circumcircle
     */
    private Set<Triangle> getCavity (Pnt site, Triangle triangle) {
        Set<Triangle> encroached = new HashSet<Triangle>();
        Queue<Triangle> toBeChecked = new LinkedList<Triangle>();
        Set<Triangle> marked = new HashSet<Triangle>();
        toBeChecked.add(triangle);
        marked.add(triangle);
        while (!toBeChecked.isEmpty()) {
            triangle = toBeChecked.remove();
            if (site.vsCircumcircle(triangle.toArray(new Pnt[0])) == 1)
                continue; // Site outside triangle => triangle not in cavity
            encroached.add(triangle);
            // Check the neighbors
            for (Triangle neighbor: triGraph.neighbors(triangle)){
                if (marked.contains(neighbor)) continue;
                marked.add(neighbor);
                toBeChecked.add(neighbor);
            }
        }
        return encroached;
    }

    /**
     * Update the triangulation by removing the cavity triangles and then
     * filling the cavity with new triangles.
     * @param site the site that created the cavity
     * @param cavity the triangles with site in their circumcircle
     * @return one of the new triangles
     */
    private Triangle update (Pnt site, Set<Triangle> cavity) {
        Set<Set<Pnt>> boundary = new HashSet<Set<Pnt>>();
        Set<Pnt> boundaryPoints = new HashSet<Pnt>();
        Set<Triangle> theTriangles = new HashSet<Triangle>();

        // Find boundary facets and adjacent triangles
        for (Triangle triangle: cavity) {
            theTriangles.addAll(neighbors(triangle));
            for (Pnt vertex: triangle) {
                Set<Pnt> facet = triangle.facetOpposite(vertex);
                if (boundary.contains(facet)) {
                    boundary.remove(facet);

                    // remove inner edge from Gabriel graph, RNG, line set
                    Pnt[] toRemove = facet.toArray(new Pnt[0]);
                    if (ggDebug) System.out.println("Gabriel: removing " + toRemove[0].toString() + ", " + toRemove[1].toString());
                    if (rngDebug) System.out.println("RNG: removing " + toRemove[0].toString() + ", " + toRemove[1].toString());
                    gabrielGraph.remove(toRemove[0], toRemove[1]);
                    rnGraph.remove(toRemove[0], toRemove[1]);
                    emstLineSet.remove(new Line(toRemove[0], toRemove[1]));

                }
                else {
                    Pnt[] toAdd = facet.toArray(new Pnt[0]);
                    boundaryPoints.add(toAdd[0]);
                    boundaryPoints.add(toAdd[1]);
                    boundary.add(facet);
                }
            }
        }
        theTriangles.removeAll(cavity);        // Adj triangles only

        // Remove the cavity triangles from the triangulation
        for (Triangle triangle: cavity) triGraph.remove(triangle);

        // Determine if new point invalidates any current Gabriel edges
        Pnt point = mostRecent.getVertexButNot(); // get some point
        Set<Pnt[]> removeGabriel = new HashSet<Pnt[]>();
        Queue<Pnt> toBeChecked = new LinkedList<Pnt>();
        Set<Pnt> visited = new HashSet<Pnt>();
        toBeChecked.add(point);
        while (!toBeChecked.isEmpty()) {
            point = toBeChecked.remove();
            if (visited.contains(point)) continue;
            Set<Pnt> neighbors = gabrielGraph.neighbors(point);
            for (Pnt neighbor: neighbors) {
                if (visited.contains(neighbor)) continue;
                toBeChecked.add(neighbor);

                Pnt c = point.midpoint(neighbor);
                double radius = point.distance(neighbor) / 2;
                if (site.inCircle(c, radius)) {
                    // site obstructs this edge - remove it
                    removeGabriel.add(new Pnt[] {point, neighbor});
                }
            }
            visited.add(point);
        }
        // Remove invalidated Gabriel edges
        for (Pnt[] toRemove: removeGabriel){
            if (ggDebug) System.out.println( "Gabriel: removing " + toRemove[0].toString() + ", " + toRemove[1].toString());
            gabrielGraph.remove(toRemove[0], toRemove[1]);
        }

        // Determine if new point invalidates any current Gabriel edges
        Set<Pnt[]> removeRNG = new HashSet<Pnt[]>();
        toBeChecked.clear();
        visited.clear();
        toBeChecked.add(point);
        while (!toBeChecked.isEmpty()) {
            point = toBeChecked.remove();
            if (visited.contains(point)) continue;
            Set<Pnt> neighbors = rnGraph.neighbors(point);
            for (Pnt neighbor: neighbors) {
                if (visited.contains(neighbor)) continue;
                toBeChecked.add(neighbor);

                Pnt c = point.midpoint(neighbor);
                double distance = point.distance(neighbor);
                if (site.inCircle(neighbor, distance) &&
                    site.inCircle(point, distance)) {
                    // site obstructs this edge - remove it
                    removeRNG.add(new Pnt[] {point, neighbor});
                }
            }
            visited.add(point);
        }
        // Remove invalidated RNG edges
        for (Pnt[] toRemove: removeRNG){
            if (rngDebug) System.out.println( "RNG: removing " + toRemove[0].toString() + ", " + toRemove[1].toString());
            rnGraph.remove(toRemove[0], toRemove[1]);
            emstLineSet.remove(new Line(toRemove[0], toRemove[1]));
        }

        // Build each new triangle and add it to the triangulation
        Set<Triangle> newTriangles = new HashSet<Triangle>();
        for (Set<Pnt> vertices: boundary) {
            vertices.add(site);
            Triangle tri = new Triangle(vertices);
            triGraph.add(tri);
            newTriangles.add(tri);
        }

        // Add new site to Gabriel graph, RNG, point set
        gabrielGraph.add(site);
        rnGraph.add(site);
        pointSet.add(site);

        // Determine if we need to add edges to Gabriel graph, RNG
        // by checking to see if any existing points lie in the Gabriel
        // circles or RNG circle intersections of the new Delaunay edges
        Set<Pnt> allPoints = gabrielGraph.nodeSet();
        Set<Pnt> newGabrielPoints = new HashSet<Pnt>();
        Set<Pnt> newRNGPoints = new HashSet<Pnt>();
        for (Pnt onBoundary: boundaryPoints) {
            newGabrielPoints.add(onBoundary);
            newRNGPoints.add(onBoundary);

            // Check GG
            Pnt c = onBoundary.midpoint(site);
            double distance = onBoundary.distance(site);
            double radius = distance / 2;
            for (Pnt toCheck : allPoints) {
                if (toCheck.inCircle(c, radius)){
                    newGabrielPoints.remove(onBoundary);
                    continue;
                }
            }

            // Check RNG
            for (Pnt toCheck : allPoints) {
                if (toCheck.inCircle(site, distance) &&
                    toCheck.inCircle(onBoundary, distance)) {
                    newRNGPoints.remove(onBoundary);
                    continue;
                }
            }
        }
        // Add new Gabriel points
        for (Pnt toAdd : newGabrielPoints) {
            if (ggDebug) System.out.println( "Gabriel: adding " + site.toString() + ", " + toAdd.toString());
            gabrielGraph.add(site, toAdd);
        }
        // Add new RNG points
        for (Pnt toAdd : newRNGPoints) {
            if (rngDebug) System.out.println( "RNG: adding " + site.toString() + ", " + toAdd.toString());
            rnGraph.add(site, toAdd);
            emstLineSet.add(new Line(site, toAdd));
        }

        // Update the graph links for each new triangle
        theTriangles.addAll(newTriangles);    // Adj triangle + new triangles
        for (Triangle triangle: newTriangles)
            for (Triangle other: theTriangles)
                if (triangle.isNeighbor(other))
                    triGraph.add(triangle, other);

        // Return one of the new triangles
        return newTriangles.iterator().next();
    }

    /**
     * Main program; used for testing.
     */
    public static void main (String[] args) {
        Triangle tri =
            new Triangle(new Pnt(-10,10), new Pnt(10,10), new Pnt(0,-10));
        System.out.println("Triangle created: " + tri);
        Triangulation dt = new Triangulation(tri);
        System.out.println("DelaunayTriangulation created: " + dt);
        dt.delaunayPlace(new Pnt(0,0));
        dt.delaunayPlace(new Pnt(1,0));
        dt.delaunayPlace(new Pnt(0,1));
        System.out.println("After adding 3 points, we have a " + dt);
        Triangle.moreInfo = true;
        System.out.println("Triangles: " + dt.triGraph.nodeSet());
    }
}