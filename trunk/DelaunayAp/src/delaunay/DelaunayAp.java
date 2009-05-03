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

/*
 * Changelog
 *
 * DATE         AUTHOR          DESCRIPTION
 * 04/04/2009   M. Deckard      Modified drawAllDelaunay() to draw vertices
 *                              and to hide edges to surrounding triangle.
 * 04/05/2009   M. Deckard      Added support for Gabriel graphs
 * 04/07/2009   A. Schwartz     Massive GUI overhaul.  Delaunay = Blue Lines/Circle  Vor = Black
 *                              Gabriel = Red.  Some issues with drawing circles remain.
 *                              It currently takes the previous color, and uses it sometimes...
 * 04/11/2009   M. Deckard      Cleaned up code a bit, making some of the naming more consistent.
 *                              Now all points are drawn after all of the graphs, and they will
 *                              show even if no graphs are selected. Also fixed color issue with
 *                              circles.
 * 04/11/2009   M. Deckard      Added support for RNGs.
 * 04/26/2009   M. Deckard      Added color choosers, created enums for graph typs and arrays
 *                              for graph elements (checkboxes, colors, etc)
 * 05/01/2009   M. Deckard      Added support for EMSTs.
 * 05/02/2009   M. Deckard      Added ability to remove points (right click) and drag points around.
 */

import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseEvent;

import javax.swing.*;

/**
 * The Delauany applet.
 *
 * Creates and displays a Delaunay Triangulation (DT) or a Voronoi Diagram
 * (VoD). Has a main program so it is an application as well as an applet.
 *
 * @author Paul Chew
 *
 * Created July 2005. Derived from an earlier, messier version.
 *
 * Modified December 2007. Updated some of the Triangulation methods. Added the
 * "Colorful" checkbox. Reorganized the interface between DelaunayAp and
 * DelaunayPanel. Added code to find a Voronoi cell.
 *
 */
@SuppressWarnings("serial")
public class DelaunayAp extends javax.swing.JApplet
        implements Runnable, ActionListener, MouseListener {

    private boolean debug = true;             // Used for debugging
    private Component currentSwitch = null;   // Entry-switch that mouse is in

    public static final int VORONOI         = 0;
    public static final int DELAUNAY        = 1;
    public static final int DELAUNAY_CIRCLE = 2;
    public static final int GABRIEL         = 3;
    public static final int GABRIEL_CIRCLE  = 4;
    public static final int RNG             = 5;
    public static final int RNG_LENS        = 6;
    public static final int EMST            = 7;
    public static final int MWT             = 8;

    public static String[] graphLabels = {
        "Voronoi Edges",
        "Delaunay Edges",
        "Delaunay Circles",
        "Gabriel Edges",
        "Gabriel Circles",
        "Relative Neighbor Graph",
        "RNG Lenses",
        "Euclidian MST",
        "Minimum Weight Triangulation"
    };

    public static Color[] graphColors = {
        Color.BLACK,
        Color.BLUE,
        Color.BLUE,
        Color.RED,
        Color.RED,
        Color.GREEN,
        Color.GREEN,
        Color.MAGENTA,
        Color.ORANGE
    };

    private JCheckBox[] graphSwitches = new JCheckBox[MWT+1];
    private JButton[] graphColorButtons = new JButton[MWT+1];
    private JPanel[] graphPanels = new JPanel[MWT+1];

    private static String windowTitle = "Graph Visualizer";
    private JButton clearButton = new JButton("Clear");
    private JCheckBox colorfulBox = new JCheckBox("More Colorful");
    private DelaunayPanel delaunayPanel = new DelaunayPanel(this);

    /**
     * Main program (used when run as application instead of applet).
     */
    public static void main (String[] args) {
        DelaunayAp applet = new DelaunayAp();    // Create applet
        applet.init();                           // Applet initialization
        JFrame dWindow = new JFrame();           // Create window
        dWindow.setSize(800, 800);               // Set window size
        dWindow.setTitle(windowTitle);           // Set window title

        dWindow.setLayout(new BorderLayout());   // Specify layout manager
        dWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                                                 // Specify closing behavior
        dWindow.add(applet, "Center");           // Place applet into window
        dWindow.setVisible(true);                // Show the window
    }

    /**
     * Initialize the applet.
     * As recommended, the actual use of Swing components takes place in the
     * event-dispatching thread.
     */
    public void init () {
        try {SwingUtilities.invokeAndWait(this);}
        catch (Exception e) {System.err.println("Initialization failure");}
    }

    /**
     * Set up the applet's GUI.
     * As recommended, the init method executes this in the event-dispatching
     * thread.
     */
    public void run () {
        setLayout(new BorderLayout());

        // Create panels
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.PAGE_AXIS));

        // Setup switches, color buttons
        for (int i = 0; i <= MWT; i++) {
            // Create switches and buttons
            graphSwitches[i]     = new JCheckBox(graphLabels[i]);
            graphColorButtons[i] = new JButton();
            graphColorButtons[i].setPreferredSize(new Dimension(16, 16));
            graphColorButtons[i].setBackground(graphColors[i]);

            // Set up panel
            graphPanels[i] = new JPanel();
            graphPanels[i].setLayout(new BoxLayout(graphPanels[i], BoxLayout.LINE_AXIS));

            // Add switches, colors to the panel
            graphPanels[i].add(graphColorButtons[i]);
            graphPanels[i].add(graphSwitches[i]);
            graphPanels[i].add(Box.createHorizontalGlue());

            // Set up ActionListeners for them
            graphColorButtons[i].addActionListener(this);
            graphSwitches[i].addActionListener(this);

            // Add graphPanels to buttonPanel
            buttonPanel.add(graphPanels[i], JPanel.LEFT_ALIGNMENT);
        }

        // Add clear button, add buttonPanel to main window
        buttonPanel.add(clearButton, JPanel.LEFT_ALIGNMENT);
        this.add(buttonPanel, BorderLayout.WEST);

        // Build the delaunay panel
        delaunayPanel.setBackground(Color.white);
        this.add(delaunayPanel, "Center");

        // Register the listeners
        clearButton.addActionListener(this);
        colorfulBox.addActionListener(this);
        delaunayPanel.addMouseListener(this);

        // Initialize the radio buttons
        graphSwitches[VORONOI].doClick();
    }

    /**
     * A button has been pressed; redraw the picture.
     */
    public void actionPerformed(ActionEvent e) {
        if (debug)
            System.out.println(((AbstractButton)e.getSource()).getText());

        if (e.getSource() == clearButton) {
            delaunayPanel.clear();
        }
        else {
            for (int i = 0; i <= MWT; i++) {
                if (e.getSource() == graphColorButtons[i]) {
                    Color newColor = JColorChooser.showDialog(this, "Choose a color for " + graphLabels[i], this.getBackground());
                    if (newColor != null) {
                        graphColors[i] = newColor;
                        graphColorButtons[i].setBackground(newColor);
                        System.out.println(graphLabels[i] + " color: " + newColor.toString());
                    }
                    break;
                }
            }
        }
        delaunayPanel.repaint();
    }

    /**
     * If entering a mouse-entry switch then redraw the picture.
     */
    public void mouseEntered(MouseEvent e) {
        currentSwitch = e.getComponent();
        if (currentSwitch instanceof JLabel) delaunayPanel.repaint();
        else currentSwitch = null;
    }

    /**
     * If exiting a mouse-entry switch then redraw the picture.
     */
    public void mouseExited(MouseEvent e) {
        currentSwitch = null;
        if (e.getComponent() instanceof JLabel) delaunayPanel.repaint();
    }

    /**
     * If mouse has been pressed inside the delaunayPanel then add a new site.
     */
    public void mousePressed(MouseEvent e) {
        if (e.getSource() != delaunayPanel) return;
        Pnt point = new Pnt(e.getX(), e.getY());
        if (debug ) {
            System.out.println("===============");
            System.out.println("Click " + point);
            System.out.println("===============");
        }

        Pnt nearest = delaunayPanel.querySite(point);
        if (e.getButton() == e.BUTTON1) {
            if (nearest.distance(point) > 5.0) {
                delaunayPanel.addSite(point);
            }
        }
        else {
            if (nearest.distance(point) < 5.0) {
                delaunayPanel.removeSite(nearest);
            }
        }
        delaunayPanel.repaint();
    }

    /**
     * Not used, but needed for MouseListener.
     */
    public void mouseReleased(MouseEvent e) {
        delaunayPanel.selectSite(null);
        delaunayPanel.repaint();
    }
    public void mouseClicked(MouseEvent e) {
    }

    /**
     * @return true iff the "colorful" box is selected
     */
    public boolean isColorful() {
        return colorfulBox.isSelected();
    }

    /**
     * Find if we are displaying a graph
     * @param graph the graph to check
     * @return true iff graph's checkbox is checked.
     */
    public boolean showingGraph(int graph) {
        return graphSwitches[graph].isSelected();
    }
}

/**
 * Graphics Panel for DelaunayAp.
 */
@SuppressWarnings("serial")
class DelaunayPanel extends JPanel
                    implements MouseMotionListener {

    public static int pointRadius = 3;

    private DelaunayAp controller;              // Controller for DT
    private Triangulation dt;                   // Delaunay triangulation
    private Map<Object, Color> colorTable;      // Remembers colors for display
    private Triangle initialTriangle;           // Initial triangle
    private static int initialSize = 10000;     // Size of initial triangle
    private Graphics g;                         // Stored graphics context
    private Random random = new Random();       // Source of random numbers
    private Pnt selectedSite;

    /**
     * Create and initialize the DT.
     */
    public DelaunayPanel (DelaunayAp controller) {
        this.controller = controller;
        initialTriangle = new Triangle(
                new Pnt(-initialSize, -initialSize),
                new Pnt( initialSize, -initialSize),
                new Pnt(           0,  initialSize));
        dt = new Triangulation(initialTriangle);
        colorTable = new HashMap<Object, Color>();
        selectedSite = new Pnt(-initialSize, -initialSize);

        // Register for mouse events
        addMouseMotionListener(this);
    }

    public void mouseMoved(MouseEvent e) {
        Pnt point = new Pnt(e.getX(), e.getY());
        Pnt nearest = dt.findNearest(point);
        if ( nearest.distance(point) < 5.0 && ( selectedSite == null || ! selectedSite.equals(nearest) ) ) {
            selectedSite = nearest;
            repaint();
        }
        else if ( nearest.distance(point) >= 5.0 &&  selectedSite != null ) {
            selectedSite = null;
            repaint();
        }
    }

    public void mouseDragged(MouseEvent e) {
        if (selectedSite != null) {
            removeSite(selectedSite);
            selectedSite = new Pnt(e.getX(), e.getY());
            addSite(selectedSite);
            repaint();
        }
    }

    /**
     * Add a new site to the DT.
     * @param point the site to add
     */
    public void addSite(Pnt point) {
        dt.delaunayPlace(point);
    }


    /**
     * Change selected site.
     * @param point the site to select
     */
    public void selectSite(Pnt point) {
        selectedSite = point;
    }

    /**
     * Query site
     * @param query the query location
     */
    public Pnt querySite(Pnt query) {
        return dt.findNearest(query);
    }

    /**
     * Remove a site from the DT.
     * @param point the site to remove
     */
    public void removeSite(Pnt point) {
        dt.delaunayRemove(point);
    }

    /**
     * Re-initialize the DT.
     */
    public void clear() {
        dt.clear();
    }

    /**
     * Get the color for the spcified item; generate a new color if necessary.
     * @param item we want the color for this item
     * @return item's color
     */
    private Color getColor (Object item) {
        if (colorTable.containsKey(item)) return colorTable.get(item);
        Color color = new Color(Color.HSBtoRGB(random.nextFloat(), 1.0f, 1.0f));
        colorTable.put(item, color);
        return color;
    }

    /* Basic Drawing Methods */

    /**
     * Draw a point.
     * @param point the Pnt to draw
     */
    public void draw (Pnt point) {
        int r = pointRadius;
        int x = (int) point.coord(0);
        int y = (int) point.coord(1);
        g.fillOval(x-r, y-r, r+r, r+r);
    }

    /**
     * Draw a point.
     * @param point the Pnt to draw
     * @param color the color to make the point
     */
    public void draw (Pnt point, Color color) {
        if (color != null) {
            Color temp = g.getColor();
            g.setColor(color);
            draw(point);
            g.setColor(temp);
        }
    }

    /**
     * Draw a circle.
     * @param center the center of the circle
     * @param radius the circle's radius
     * @param color color of circle (null = default color)
     */
    public void draw (Pnt center, double radius, Color color) {
        int x = (int) center.coord(0);
        int y = (int) center.coord(1);
        int r = (int) radius;
        if (color != null) {
            Color temp = g.getColor();
            g.setColor(color);
            g.drawOval(x-r, y-r, r+r, r+r);
            g.setColor(Color.GREEN);
            g.setColor(temp);
        }
        else {
            g.drawOval(x-r, y-r, r+r, r+r);
        }
    }

    /**
     * Draw an arc of a circle.
     * @param center the center of the circle
     * @param radius the circle's radius
     * @param startAngle the beginning angle.
     * @param arcAngle the angular extent of the arc, relative to the start angle.
     * @param color color of circle (null = default color)
     */
    public void drawArc (Pnt center, double radius, double startAngle, double arcAngle, Color color) {
        int x = (int) center.coord(0);
        int y = (int) center.coord(1);
        int r = (int) radius;
        int start = (int) startAngle;
        int arc = (int) arcAngle;
        if (color != null) {
            Color temp = g.getColor();
            g.setColor(color);
            g.drawArc(x-r, y-r, r+r, r+r, start, arc);
            g.setColor(Color.GREEN);
            g.setColor(temp);
        }
        else {
            g.drawArc(x-r, y-r, r+r, r+r, start, arc);
        }
    }

    /**
     * Draw a polygon.
     * @param polygon an array of polygon vertices
     * @param color color of polygon (null = default color)
     */
    public void draw (Pnt[] polygon, Color color) {
        int[] x = new int[polygon.length];
        int[] y = new int[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            x[i] = (int) polygon[i].coord(0);
            y[i] = (int) polygon[i].coord(1);
        }
        if (color != null) {
            Color temp = g.getColor();
            g.setColor(color);
            g.drawPolygon(x, y, polygon.length);
            g.setColor(temp);
        }
        else {
            g.drawPolygon(x, y, polygon.length);
        }
    }

    /* Higher Level Drawing Methods */

    /**
     * Handles painting entire contents of DelaunayPanel.
     * Called automatically; requested via call to repaint().
     * @param g the Graphics context
     */
    public void paintComponent (Graphics g) {
        super.paintComponent(g);
        this.g = g;

        // Flood the drawing area with a "background" color
       /* Color temp = g.getColor();
        if (!controller.isVoronoi()) g.setColor(delaunayColor);
        else if (dt.contains(initialTriangle)) g.setColor(this.getBackground());
        else g.setColor(voronoiColor);
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
        g.setColor(temp);*/

        // If no colors then we can clear the color table
        if (!controller.isColorful()) colorTable.clear();

        // Draw all selected graphs and circles
        if (controller.showingGraph(DelaunayAp.VORONOI))
            drawAllVoronoi(controller.isColorful(), false);
        if (controller.showingGraph(DelaunayAp.DELAUNAY))
            drawAllDelaunay(controller.isColorful(), false);
        if (controller.showingGraph(DelaunayAp.GABRIEL))
            drawAllGabriel(false);
        if (controller.showingGraph(DelaunayAp.RNG))
            drawAllRNG(false);
        if (controller.showingGraph(DelaunayAp.DELAUNAY_CIRCLE))
            drawAllDelaunayCircles();
        if (controller.showingGraph(DelaunayAp.GABRIEL_CIRCLE))
            drawAllGabrielCircles();
        if (controller.showingGraph(DelaunayAp.RNG_LENS))
            drawAllRNGLenses();
        if (controller.showingGraph(DelaunayAp.EMST))
            drawAllEMST();
        if (controller.showingGraph(DelaunayAp.MWT))
            drawAllMWT();

        // Now, draw the points
        drawAllPoints();
    }

    /**
     * Draw all the Delaunay triangles.
     * @param withFill true iff drawing Delaunay triangles with fill colors
     */
    public void drawAllDelaunay (boolean withFill, boolean withSites) {
        // Keep track of sites done; no drawing for initial triangles sites
        HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

        for (Triangle triangle : dt) {
            if (! triangle.containsAny(initialTriangle)) {
                Pnt[] vertices = triangle.toArray(new Pnt[0]);
                //draw(vertices, withFill? getColor(triangle) : null);
                draw(vertices, withFill? getColor(triangle) : DelaunayAp.graphColors[DelaunayAp.DELAUNAY]);
            }

            if (withSites)
            {
               for (Pnt site: triangle) {
                    if (done.contains(site)) continue;
                    done.add(site);
                    draw(site);
                }
            }
        }
    }

    /**
     * Draw all the Voronoi cells.
     * @param withFill true iff drawing Voronoi cells with fill colors
     * @param withSites true iff drawing the site for each Voronoi cell
     */
    public void drawAllVoronoi (boolean withFill, boolean withSites) {
        // Keep track of sites done; no drawing for initial triangles sites
        HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

        for (Triangle triangle : dt)
            for (Pnt site: triangle) {
                if (done.contains(site)) continue;
                done.add(site);
                List<Triangle> list = dt.surroundingTriangles(site, triangle);
                Pnt[] vertices = new Pnt[list.size()];
                int i = 0;
                for (Triangle tri: list)
                    vertices[i++] = tri.getCircumcenter();
                draw(vertices, withFill? getColor(site) : DelaunayAp.graphColors[DelaunayAp.VORONOI]);
                if (withSites) draw(site);
            }
    }

    /**
     * Draw all the Relative Neighbor Graph edges.
     * @param withSites true iff drawing the site for each point
     */
    public void drawAllRNG (boolean withSites){
        /*
         * NOTE: This currently does double processing it needs to, processing
         * every edge twice: one for each triangle the edge is on.
         */

        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {

            // Keep track of sites done; no drawing for initial triangles sites
            HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

            for (Pnt site: triangle) {
                if (done.contains(site)) continue;
                done.add(site);
                if (withSites) draw(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.rngEdge(site, site2) && ! done.contains(site2)) {
                    Pnt[] vertices = { site, site2 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.RNG]);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.rngEdge(site, site3) && ! done.contains(site3)) {
                    Pnt[] vertices = { site, site3 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.RNG]);
                }
            }
        }
    }
    /**
     * Draw all the Euclidean Minimum Spanning Tree edges.
     */
    public void drawAllEMST (){
        /*
         * NOTE: This currently does double processing it needs to, processing
         * every edge twice: one for each triangle the edge is on.
         */

        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {

            // Keep track of sites done; no drawing for initial triangles sites
            HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

            for (Pnt site: triangle) {
                if (done.contains(site)) continue;
                done.add(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.emstEdge(site, site2) && ! done.contains(site2)) {
                    Pnt[] vertices = { site, site2 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.EMST]);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.emstEdge(site, site3) && ! done.contains(site3)) {
                    Pnt[] vertices = { site, site3 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.EMST]);
                }
            }
        }
    }
    
    /**
     * Draw all the Minimum Weight Triangulation edges.
     */
    public void drawAllMWT (){

        //CHANGE TO BE FOR MWT
           for (Triangle triangle: dt) {

            // Keep track of sites done; no drawing for initial triangles sites
            HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

            for (Pnt site: triangle) {
                if (done.contains(site)) continue;
                done.add(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.emstEdge(site, site2) && ! done.contains(site2)) {
                    Pnt[] vertices = { site, site2 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.MWT]);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.emstEdge(site, site3) && ! done.contains(site3)) {
                    Pnt[] vertices = { site, site3 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.MWT]);
                }
            }
        }
    }

    /**
     * Draw all the points.
     */
    public void drawAllPoints (){
        // Keep track of sites done; no drawing for initial triangles sites
        HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

        for (Triangle triangle : dt) {
            for (Pnt site: triangle) {
                 if (done.contains(site)) continue;
                 done.add(site);
                 if (site.equals(selectedSite)) {
                    draw(site, Color.RED);
                 }
                 else {
                    draw(site);
                 }
             }
        }
    }

    /**
     * Draw all the Gabriel edges.
     * @param withSites true iff drawing the site for each point
     */
    public void drawAllGabriel (boolean withSites) {
        /*
         * NOTE: This currently does double processing it needs to, processing
         * every edge twice: one for each triangle the edge is on.
         */

        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {

            // Keep track of sites done; no drawing for initial triangles sites
            HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

            for (Pnt site: triangle) {
                if (done.contains(site)) continue;
                done.add(site);
                if (withSites) draw(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.gabrielEdge(site, site2) && ! done.contains(site2)) {
                    Pnt[] vertices = { site, site2 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.GABRIEL]);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.gabrielEdge(site, site3) && ! done.contains(site3)) {
                    Pnt[] vertices = { site, site3 };
                    draw(vertices, DelaunayAp.graphColors[DelaunayAp.GABRIEL]);
                }
            }
        }
    }

    /**
     * Draw all the empty circles (one for each pair of points) of the Gabriel graph.
     */
    public void drawAllGabrielCircles () {
        /*
         * NOTE: This currently does double processing it needs to, processing
         * every edge twice: one for each triangle the edge is on.
         */

        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {
            // Skip circles involving the initial-triangle vertices
            if (triangle.containsAny(initialTriangle)) continue;

            // Keep track of sites done; no drawing for initial triangles sites
            HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

            for (Pnt site: triangle) {
                done.add(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.gabrielEdge(site, site2) && ! done.contains(site2)) {
                    Pnt c = site.midpoint(site2);
                    double radius = site.distance(site2) / 2;
                    draw(c, radius, DelaunayAp.graphColors[DelaunayAp.GABRIEL_CIRCLE]);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.gabrielEdge(site, site3) && ! done.contains(site3)) {
                    Pnt c = site.midpoint(site3);
                    double radius = site.distance(site3) / 2;
                    draw(c, radius, DelaunayAp.graphColors[DelaunayAp.GABRIEL_CIRCLE]);
                }
            }
        }
    }

    /**
     * Draw all the empty lenses (one for each pair of points) of the RNG.
     */
    public void drawAllRNGLenses () {
        /*
         * NOTE: This currently does double processing it needs to, processing
         * every edge twice: one for each triangle the edge is on.
         */

        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {
            // Skip circles involving the initial-triangle vertices
            if (triangle.containsAny(initialTriangle)) continue;

            // Keep track of sites done; no drawing for initial triangles sites
            HashSet<Pnt> done = new HashSet<Pnt>(initialTriangle);

            for (Pnt site: triangle) {
                done.add(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.rngEdge(site, site2) && ! done.contains(site2)) {
                    double angle1 = site.angle2(site2, false);
                    double angle2 = site2.angle2(site, false);
                    double distance = site.distance(site2);
                    drawArc(site, distance, -angle1 - 60, 120.0, DelaunayAp.graphColors[DelaunayAp.RNG_LENS]);
                    drawArc(site2, distance, -angle2 - 60, 120.0, DelaunayAp.graphColors[DelaunayAp.RNG_LENS]);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.rngEdge(site, site3) && ! done.contains(site3)) {
                    double angle1 = site.angle2(site3, false);
                    double angle2 = site3.angle2(site, false);
                    double distance = site.distance(site3);
                    drawArc(site, distance, -angle1 - 60, 120.0, DelaunayAp.graphColors[DelaunayAp.RNG_LENS]);
                    drawArc(site3, distance, -angle2 - 60, 120.0, DelaunayAp.graphColors[DelaunayAp.RNG_LENS]);
                }
            }
        }
    }

    /**
     * Draw all the empty circles (one for each triangle) of the DT.
     */
    public void drawAllDelaunayCircles () {
        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {
            // Skip circles involving the initial-triangle vertices
            if (triangle.containsAny(initialTriangle)) continue;
            Pnt c = triangle.getCircumcenter();
            double radius = c.subtract(triangle.get(0)).magnitude();
            
            draw(c, radius, DelaunayAp.graphColors[DelaunayAp.DELAUNAY_CIRCLE]);
            
        }
    }

}
