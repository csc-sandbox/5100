package edu.calpoly.robot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * RobotDisplayPanel is a GUI class for drawing and updating a Robot.
 * Typical usage is creating an instance, adding the instance to a JPanel, and then updating the instance.
 * 
 * @author Paul Motter (PaulMotter)
 * @version 1.0.0 (9/24/2026)
 */
class RobotDisplayPanel extends JPanel {
    // Class variables for uniformity.
    private static final int DEFAULT_HEIGHT = 640;
    private static final int DEFAULT_WIDTH = 640;
    private static final Color[] BAR_COLORS = {
        Color.red, Color.orange, Color.yellow, Color.green, Color.blue, Color.magenta,
    };
    private static final Color BASE_COLOR = Color.gray; 
    private static final Color OUTLINE_COLOR = Color.white;
    private static final Color BACKGROUND_COLOR = Color.black;

    // Image that is drawn. Owned by and managed by the instance.
    private BufferedImage image;

    /**
     * Create a RobotDisplay overwritting the default width and height of the image.
     * @param bufferWidth The resolution of the resulting image width.
     * @param bufferHeight The resolution of the resulting image height.
     */
    RobotDisplayPanel(int bufferWidth, int bufferHeight){
        image = new BufferedImage(bufferWidth, bufferHeight, BufferedImage.TYPE_INT_ARGB);
        this.setBackground(BACKGROUND_COLOR);
    }

    /**
     * Create a display with the default resolution for the image.
     * For the default resolution see DEFAULT_WIDTH and DEFAULT_HEIGHT.
     */
    RobotDisplayPanel(){
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Clears the previous image and draws a new image given the provided data. The final image is then drawn to the screen.
     * @param data The RobotMessage you would like to be displayed.
     */
    public void update(RobotMessage data){
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            );

            // Wipe the previous frame.
            g.setColor(BACKGROUND_COLOR);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setStroke(new BasicStroke(2));

            // Draw Robot
            float barLength = Math.min(image.getWidth(), image.getHeight())/(float)(2*data.jointAngles.length);
            float halfBarWidth = barLength/3.0f/2.0f;
            // array of points.
            float[][] pos = new float[data.jointAngles.length+1][2];
            pos[0][0] = image.getWidth()/2.0f;
            pos[0][1] = image.getHeight()*0.60f;
            // drawing arm rectangles.
            for (int i=0; i<data.jointAngles.length; ++i){
                // drawing Robot Arms
                    // Intermediates
                float angle = data.jointAngles[i];
                pos[i+1][0] = pos[i][0]+barLength*(float)Math.cos(angle);
                pos[i+1][1] = pos[i][1]-barLength*(float)Math.sin(angle);
                float yWidth = +halfBarWidth*(float)Math.cos(angle);
                float xWidth = +halfBarWidth*(float)Math.sin(angle);
                    // calulate polygon for the bar.
                int[] xpoints = {
                    (int)(pos[i][0]+xWidth), 
                    (int)(pos[i+1][0]+xWidth), 
                    (int)(pos[i+1][0]-xWidth),
                    (int)(pos[i][0]-xWidth), 
                };
                int[] ypoints = {
                    (int)(pos[i][1]+yWidth), 
                    (int)(pos[i+1][1]+yWidth), 
                    (int)(pos[i+1][1]-yWidth),
                    (int)(pos[i][1]-yWidth), 
                };
                g.setColor(BAR_COLORS[i]);
                g.fillPolygon(new Polygon(xpoints, ypoints, 4));
                g.setColor(OUTLINE_COLOR);
                g.drawPolyline(xpoints, ypoints, 4);
                // Draw base only after the first arm
                if (i==0){
                    g.setColor(BASE_COLOR);
                    g.fillRect(
                        (int)(pos[i][0]-2.5*halfBarWidth), (int)(pos[i][1]+0*halfBarWidth),
                        (int)(5*halfBarWidth), (int)(3*halfBarWidth)
                    );
                    g.setColor(OUTLINE_COLOR);
                    g.drawRect(
                        (int)(pos[i][0]-2.5*halfBarWidth), (int)(pos[i][1]+0*halfBarWidth),
                        (int)(5*halfBarWidth), (int)(3*halfBarWidth)
                    );
                }
            }
            // Drawing joint circles
            g.setColor(BASE_COLOR);
            for (int i=0; i<data.jointAngles.length; ++i){
                g.setColor(BASE_COLOR);
                g.fillOval(
                    (int)(pos[i][0]-halfBarWidth), (int)(pos[i][1]-halfBarWidth),
                    (int)(2*halfBarWidth), (int)(2*halfBarWidth)
                );
                g.setColor(OUTLINE_COLOR);
                g.drawOval(
                    (int)(pos[i][0]-halfBarWidth), (int)(pos[i][1]-halfBarWidth),
                    (int)(2*halfBarWidth), (int)(2*halfBarWidth)
                );
            }
        }
        finally {
            g.dispose();
        }

        repaint();
    }

    // Keeps the rendered image the correct size given the frame size.
    // Allows the window/frame to be resized and not clip or stretch the drawing.
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image == null) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        // Higher-quality scaling
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );

        // Get informatino about the canvas.
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        // scale
        double scale = Math.min(
            (double) panelWidth / imageWidth,
            (double) panelHeight / imageHeight
        );

        int newWidth = (int) (imageWidth * scale);
        int newHeight = (int) (imageHeight * scale);

        // Center the image
        int x = (panelWidth - newWidth) / 2;
        int y = (panelHeight - newHeight) / 2;

        g2.drawImage(
            image,
            x, y,
            newWidth, newHeight,
            null
        );

        g2.dispose();
    }
}
