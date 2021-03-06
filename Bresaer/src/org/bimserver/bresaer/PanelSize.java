package org.bimserver.bresaer;

public class PanelSize {
	public int width;
	public int height;
	
	public PanelSize(int w, int h) {
		width = w;
		height = h;
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof PanelSize)) {
	            return false;
	    }
		PanelSize panelSize = (PanelSize) o;
		 
		return panelSize.width == width && panelSize.height == height;
	}		
		
	@Override 
	public int hashCode() {
        int result = 19; 
        result = 67 * result + Integer.hashCode(width);
        result = 67 * result + Integer.hashCode(height);
        return result;
	}		
}
