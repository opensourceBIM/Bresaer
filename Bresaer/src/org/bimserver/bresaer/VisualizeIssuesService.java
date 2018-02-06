package org.bimserver.bresaer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;


import org.bimserver.bimbots.BimBotsException;
import org.bimserver.bimbots.BimBotsInput;
import org.bimserver.bimbots.BimBotsOutput;
import org.bimserver.bresaer.Panel.PanelType;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SObjectType;
import org.bimserver.models.geometry.GeometryInfo;
import org.bimserver.models.ifc2x3tc1.IfcBuildingElementProxy;
import org.bimserver.models.ifc2x3tc1.IfcOpeningElement;
import org.bimserver.plugins.SchemaName;
import org.bimserver.plugins.services.BimBotAbstractService;

import com.google.common.base.Charsets;

public class VisualizeIssuesService extends BimBotAbstractService {

	private HashMap<Plane, HashSet<Panel>>     panelsByPlane  = new HashMap<Plane, HashSet<Panel>>();
	private HashSet<Panel>                     eurecatPanels  = new HashSet<Panel>();
	
	
	private void GetPanelsFromBIM(IfcModelInterface model)	{		
		// Panels are stored as IfcBuildingElementProxy, so get all of them and loop through them for analysing each panel
		List<IfcBuildingElementProxy> allWithSubTypes = model.getAllWithSubTypes(IfcBuildingElementProxy.class);
		for (IfcBuildingElementProxy ifcProxy : allWithSubTypes) {
			//determine if the proxy is a panel (contains "initial" and "family" in the type string) or not
			if (!ifcProxy.getObjectType().contains("frame") && !ifcProxy.getObjectType().contains("blind"))
				continue; // no panel so continue to the next proxy 
						
			//create a listing of the panels based on each corner => a list contains neighbouring panel
			Panel curPanel = new Panel(ifcProxy);
			if (curPanel.normalAxis == -1)
				continue;
					
			if (curPanel.type == PanelType.EURECAT) {
				eurecatPanels.add(curPanel);
				continue;
			}
			
			Coordinate coor = new Coordinate(curPanel.positiveNormal ? curPanel.min : curPanel.max);
			
			// Create a plane object for the current plane, with the origin depening on the normals direction +/-
			Plane plane = new Plane(curPanel.normalAxis, coor); 

			// Find listing of the panels for the current plane
			if (!panelsByPlane.containsKey(plane))
				panelsByPlane.put(plane, new HashSet<Panel>());
				
			panelsByPlane.get(plane).add(curPanel);
		}
	}
	
	
	private void GetIntersections(IfcModelInterface model) {
		Coordinate min, max;
	
		// Panels are stored as IfcBuildingElementProxy, so get all of them and loop through them for analysing each panel
		List<IfcOpeningElement> openings = model.getAllWithSubTypes(IfcOpeningElement.class);
		for (IfcOpeningElement opening : openings) {
			/*
			// Only voids in the external walls are relevant 
			IfcElement inElement = opening.getVoidsElements().getRelatingBuildingElement();
			*/
			GeometryInfo gInfo = opening.getGeometry();
			if (gInfo != null) {
				min  = new Coordinate(gInfo.getMinBounds().getX(), 
						   			  gInfo.getMinBounds().getY(),
						   			  gInfo.getMinBounds().getZ());
				max  = new Coordinate(gInfo.getMaxBounds().getX(), 
						   			  gInfo.getMaxBounds().getY(),
						   			  gInfo.getMaxBounds().getZ());

				// find panels covering the opening
				// find the matching plane by checking each plane
				for (HashSet<Panel> panels : panelsByPlane.values()) {
					
					// get a panel from the list to have the corresponding axis definition
					Panel refPanel = panels.iterator().next();
					
					if ((refPanel.positiveNormal && min.v[refPanel.normalAxis] <= refPanel.min.v[refPanel.normalAxis] && 
							                     max.v[refPanel.normalAxis] >= refPanel.min.v[refPanel.normalAxis]) || 
						(!refPanel.positiveNormal && min.v[refPanel.normalAxis] <= refPanel.max.v[refPanel.normalAxis] && 
		                                          max.v[refPanel.normalAxis] >= refPanel.max.v[refPanel.normalAxis])) {
						// the current plane interferes with the current opening
						for (Panel panel : panels) {
							if (panel.min.v[panel.widthAxis()] < max.v[panel.widthAxis()] && 
							    panel.max.v[panel.widthAxis()] > min.v[panel.widthAxis()] &&
								panel.min.v[panel.upAxis] < max.v[panel.upAxis] &&
								panel.max.v[panel.upAxis] > min.v[panel.upAxis]) {
								panel.coversOpening = true;
							}
						}
						break;
					}
				}
				
				//find eurecat panels covering part of the opening
				for (Panel panel : eurecatPanels) { 
					if (panel.min.v[panel.normalAxis] < max.v[panel.normalAxis] &&
					    panel.max.v[panel.normalAxis] > min.v[panel.normalAxis] &&
						panel.min.v[panel.widthAxis()] < max.v[panel.widthAxis()] && 
						panel.max.v[panel.widthAxis()] > min.v[panel.widthAxis()] &&
						panel.min.v[panel.upAxis] < max.v[panel.upAxis] &&
						panel.max.v[panel.upAxis] > min.v[panel.upAxis] &&
						(panel.min.v[panel.widthAxis()] + panel.offset[0] > min.v[panel.widthAxis()] || 
						 panel.max.v[panel.widthAxis()] - panel.offset[1] < max.v[panel.widthAxis()] ||
						 panel.min.v[panel.upAxis] + panel.offset[3] > min.v[panel.upAxis] ||
						 panel.max.v[panel.upAxis] - panel.offset[2] < max.v[panel.upAxis])) {
						panel.coversOpening = true;
					}									
				}
			}
		}
	}	
	

	
	public String GenerateColoredJSON() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("  \"name\": \"Bresaer elements with issues test\",\n");
		sb.append("	 \"changes\": [\n");
		sb.append("    {\n");
		sb.append("      \"selector\": {\n");
		sb.append("        \"guids\": [\n");
		boolean first = true;
		for (HashSet<Panel> panels : panelsByPlane.values()) {
			for (Panel panel : panels) {
				if (panel.coversOpening) {
					if (first) {
						first = false;
						sb.append("\"" + panel.id + "\"");
					}
					else {
						sb.append(",\n\"" + panel.id + "\"");
					}
				}		
			}
		}	
		for (Panel panel : eurecatPanels) {
			if (panel.coversOpening) {
				if (first) {
					first = false;
					sb.append("\"" + panel.id + "\"");
				}
				else {
					sb.append(",\n\"" + panel.id + "\"");
				}
			}		

		}		
		sb.append("        ]\n");
		sb.append("      },\n");
		sb.append("      \"effect\": {\n");
		sb.append("        \"color\": {\n");
		sb.append("          \"r\": 1,\n");
		sb.append("          \"g\": 0,\n");
		sb.append("          \"b\": 0,\n");
		sb.append("          \"a\": 1\n");
		sb.append("        }\n");
		sb.append("      }\n");
		sb.append("    }\n");
		sb.append("  ]\n");
		sb.append("}\n");

		return sb.toString();
	}
	
	
	@Override
	public BimBotsOutput runBimBot(BimBotsInput input, SObjectType settings) throws BimBotsException {
		
		panelsByPlane.clear();
		eurecatPanels.clear();
		
		IfcModelInterface model = input.getIfcModel();		
		GetPanelsFromBIM(model);
		GetIntersections(model);
				
		String outputString = GenerateColoredJSON();
	
		BimBotsOutput output = new BimBotsOutput(SchemaName.VIS_3D_JSON_1_0, outputString.getBytes(Charsets.UTF_8));
		output.setTitle("Bresaer colorize issues");
		output.setContentType("application/json");	
		return output;
	}
	

	@Override
	public String getOutputSchema() {
		return SchemaName.VIS_3D_JSON_1_0.name();
	}
}