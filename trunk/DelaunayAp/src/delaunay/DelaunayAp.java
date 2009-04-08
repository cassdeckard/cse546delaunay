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
 */

import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    private Component currentSwitch = null;    // Entry-switch that mouse is in

    private static String windowTitle = "Graph Visualizer";
    private JCheckBox voronoiButton = new JCheckBox("Voronoi Diagram");
    private JCheckBox delaunayButton =  new JCheckBox("Delaunay Triangulation");
    private JCheckBox ggButton =  new JCheckBox("Gabriel Graph");
    private JCheckBox emstButton =  new JCheckBox("Euclidean MST");
    private JCheckBox rngButton =  new JCheckBox("Relative Neighbor Graph");
    private JButton clearButton = new JButton("Clear");
    private JCheckBox colorfulBox = new JCheckBox("More Colorful");
    private DelaunayPanel delaunayPanel = new DelaunayPanel(this);
    private JCheckBox circleSwitch = new JCheckBox("Show Delaunay Circles");
    private JCheckBox delaunaySwitch = new JCheckBox("Show Delaunay Edges");
    private JCheckBox gabrielSwitch = new JCheckBox("Show Gabriel Edges");
    private JCheckBox voronoiSwitch = new JCheckBox("Show Voronoi Edges");
    private JCheckBox gabrielCircleSwitch = new JCheckBox("Show Gabriel Circles");

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
        
        // Add the button controls
        /*ButtonGroup group = new ButtonGroup();
        group.add(voronoiButton);
        group.add(delaunayButton);
        group.add(ggButton);*/
        
        JPanel buttonPanel = new JPanel();
        
        buttonPanel.setLayout(new GridLayout(15, 1));
        buttonPanel.add(voronoiButton, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(delaunayButton, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(circleSwitch, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(ggButton, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(gabrielCircleSwitch, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(rngButton, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(emstButton, JPanel.LEFT_ALIGNMENT);
        buttonPanel.add(clearButton, JPanel.LEFT_ALIGNMENT);
        this.add(buttonPanel, BorderLayout.WEST);

        // Build the delaunay panel
        delaunayPanel.setBackground(Color.white);
        this.add(delaunayPanel, "Center");

        // Register the listeners
        voronoiButton.addActionListener(this);
        delaunayButton.addActionListener(this);
        ggButton.addActionListener(this);
        rngButton.addActionListener(this);
        emstButton.addActionListener(this);
        clearButton.addActionListener(this);
        colorfulBox.addActionListener(this);
        delaunayPanel.addMouseListener(this);
        gabrielCircleSwitch.addActionListener(this);
        circleSwitch.addActionListener(this);
        gabrielSwitch.addActionListener(this);
        delaunaySwitch.addActionListener(this);
        voronoiSwitch.addActionListener(this);

        // Initialize the radio buttons
        voronoiButton.doClick();
    }

    /**
     * A button has been pressed; redraw the picture.
     */
    public void actionPerformed(ActionEvent e) {
        if (debug)
            System.out.println(((AbstractButton)e.getSource()).getText());
        if (e.getSource() == clearButton) delaunayPanel.clear();
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
        delaunayPanel.addSite(point);
        delaunayPanel.repaint();
    }

    /**
     * Not used, but needed for MouseListener.
     */
    public void mouseReleased(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {
    }

    /**
     * @return true iff the "colorful" box is selected
     */
    public boolean isColorful() {
        return colorfulBox.isSelected();
    }

    /**
     * @return true iff doing Voronoi diagram.
     */
    public boolean isVoronoi() {
        return voronoiButton.isSelected();
    }

    /**
     * @return true iff doing Delaunay triangulation.
     */
    public boolean isDelaunay() {
        return delaunayButton.isSelected();
    }

    /**
     * @return true iff doing Gabriel graph.
     */
    public boolean isGabriel() {
        return ggButton.isSelected();
    }
    
    public boolean isRNG() {
        return rngButton.isSelected();
    }
    
    public boolean isEMST() {
        return emstButton.isSelected();
    }

    /**
     * @return true iff within Gabriel circle switch
     */
    public boolean showingGabrielCircles() {
        return gabrielCircleSwitch.isSelected();//currentSwitch == gabrielCircleSwitch;
    }

    /**
     * @return true iff within circle switch
     */
    public boolean showingCircles() {
        return circleSwitch.isSelected();//currentSwitch == circleSwitch;
    }

    /**
     * @return true iff within Gabriel switch
     */
    public boolean showingGabriel() {
        return gabrielSwitch.isSelected();//currentSwitch == gabrielSwitch;
    }

    /**
     * @return true iff within delaunay switch
     */
    public boolean showingDelaunay() {
        return delaunaySwitch.isSelected();//currentSwitch == delaunaySwitch;
    }

    /**
     * @return true iff within voronoi switch
     */
    public boolean showingVoronoi() {
        return voronoiSwitch.isSelected();//currentSwitch == voronoiSwitch;
    }

}

/**
 * Graphics Panel for DelaunayAp.
 */
@SuppressWarnings("serial")
class DelaunayPanel extends JPanel {

    public static Color voronoiColor = Color.magenta;
    public static Color delaunayColor = Color.green;
    public static int pointRadius = 3;

    private DelaunayAp controller;              // Controller for DT
    private Triangulation dt;                   // Delaunay triangulation
    private Map<Object, Color> colorTable;      // Remembers colors for display
    private Triangle initialTriangle;           // Initial triangle
    private static int initialSize = 10000;     // Size of initial triangle
    private Graphics g;                         // Stored graphics context
    private Random random = new Random();       // Source of random numbers

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
    }

    /**
     * Add a new site to the DT.
     * @param point the site to add
     */
    public void addSite(Pnt point) {
        dt.delaunayPlace(point);
    }

    /**
     * Re-initialize the DT.
     */
    public void clear() {
        dt = new Triangulation(initialTriangle);
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
     * Draw a circle.
     * @param center the center of the circle
     * @param radius the circle's radius
     * @param fillColor null implies no fill
     */
    public void draw (Pnt center, double radius, Color fillColor) {
        int x = (int) center.coord(0);
        int y = (int) center.coord(1);
        int r = (int) radius;
        /*if (fillColor != null) {
            Color temp = g.getColor();
            g.setColor(fillColor);
            g.fillOval(x-r, y-r, r+r, r+r);
            
            g.setColor(temp);
        }*/
        g.drawOval(x-r, y-r, r+r, r+r);

            System.out.println("===============");
            System.out.println("Click " + fillColor);
            System.out.println("===============");

        g.setColor(fillColor);
    }

    /**
     * Draw a polygon.
     * @param polygon an array of polygon vertices
     * @param fillColor null implies no fill
     */
    public void draw (Pnt[] polygon, Color fillColor) {
        int[] x = new int[polygon.length];
        int[] y = new int[polygon.length];
        for (int i = 0; i < polygon.length; i++) {
            x[i] = (int) polygon[i].coord(0);
            y[i] = (int) polygon[i].coord(1);
        }
        /*if (fillColor != null) {
            Color temp = g.getColor();
            g.setColor(fillColor);
            g.fillPolygon(x, y, polygon.length);
            g.setColor(temp);
        }*/
        g.setColor(fillColor);
        g.drawPolygon(x, y, polygon.length);
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

        // Draw the appropriate picture
        if (controller.isVoronoi())
            drawAllVoronoi(controller.isColorful(), true);
        if (controller.isDelaunay())
            drawAllDelaunay(controller.isColorful(), true);
        if (controller.isGabriel())
            drawAllGabriel(true);
        if (controller.isRNG())
            drawAllEMST(true);
        if (controller.isEMST())
            drawAllEMST(true);

        // Draw any extra info due to the mouse-entry switches
        //temp = g.getColor();
        //g.setColor(Color.gray);
        if (controller.showingGabrielCircles()) drawAllGabrielCircles();
        if (controller.showingCircles()) drawAllCircles();
        if (controller.showingGabriel()) drawAllGabriel(false);
        if (controller.showingDelaunay()) drawAllDelaunay(false, false);
        if (controller.showingVoronoi()) drawAllVoronoi(false, false);
       // g.setColor(temp);
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
                draw(vertices, withFill? getColor(triangle) : Color.blue);
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
                draw(vertices, withFill? getColor(site) : null);
                //draw(vertices, Color.blue);
                if (withSites) draw(site);
            }
    }

    public void drawAllEMST (boolean withSites){
    
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
                draw(site);
                Pnt site2 = triangle.getVertexButNot(site); // get another vertex
                if ( dt.gabrielEdge(site, site2) && ! done.contains(site2)) {
                    Pnt[] vertices = { site, site2 };
                    draw(vertices, Color.red);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.gabrielEdge(site, site3) && ! done.contains(site3)) {
                    Pnt[] vertices = { site, site3 };
                    draw(vertices, Color.red);
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
                    draw(c, radius, Color.red);
                }
                Pnt site3 = triangle.getVertexButNot(site, site2); // get another vertex
                if ( dt.gabrielEdge(site, site3) && ! done.contains(site3)) {
                    Pnt c = site.midpoint(site3);
                    double radius = site.distance(site3) / 2;
                    draw(c, radius, Color.red);
                }
            }
        }
    }

    /**
     * Draw all the empty circles (one for each triangle) of the DT.
     */
    public void drawAllCircles () {
        // Loop through all triangles of the DT
        for (Triangle triangle: dt) {
            // Skip circles involving the initial-triangle vertices
            if (triangle.containsAny(initialTriangle)) continue;
            Pnt c = triangle.getCircumcenter();
            double radius = c.subtract(triangle.get(0)).magnitude();
            
            draw(c, radius, Color.blue);
            
        }
    }

}
