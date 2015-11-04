
package com.limegroup.gnutella.gui.tables;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;

import javax.swing.Icon;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

/**
 * An Aqua style sort arrow (a filled gray triangle)
 */
public class AquaSortArrowIcon extends SortArrowIcon {

    private static final Icon DESCENDING_ICON = new AquaSortArrowIcon(DESCENDING);
    private static final Icon ASCENDING_ICON = new AquaSortArrowIcon(ASCENDING);
	
	private static final Color ARROW_GRAY = new Color(89, 93, 97);
	private static final int BLUR_FIX = 1;
    
    public static Icon getAscendingIcon() {
        return ASCENDING_ICON;
    }
    
    public static Icon getDescendingIcon() {
        return DESCENDING_ICON;
    }
	
	public AquaSortArrowIcon(int direction) {
		super(direction);
	}

	public void paintIcon(Component c, Graphics g, int x, int y) {
		if (direction == NONE)
			return;
			
		LookAndFeel lf = UIManager.getLookAndFeel();
		
		// fallback for themes
		if (lf == null || !lf.isNativeLookAndFeel()) {
			super.paintIcon(c, g, x, y);
			return;
		}
		
		Dimension size = c.getSize();
		
		Graphics2D g2 = (Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		float w = 7;
		float h = 7;
		float m = w / 2.0f;
		
		float x0 = size.width - 12;
		float y0 = (size.height - h - BLUR_FIX) / 2.0f;
		
		g2.setColor(ARROW_GRAY);
		
		GeneralPath arrow = new GeneralPath();
		
		switch (direction) {
			case DESCENDING:
				arrow.moveTo(x0,   y0);
				arrow.lineTo(x0+w, y0);
				arrow.lineTo(x0+m, y0+h);
				arrow.closePath();
				break;
			case ASCENDING:
				arrow.moveTo(x0,   y0+h);
				arrow.lineTo(x0+w, y0+h);
				arrow.lineTo(x0+m, y0);
				arrow.closePath();
				break;
		}
		
		g2.fill(arrow);
	}
}

