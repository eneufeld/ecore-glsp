package com.eclipsesource.glsp.ecore.diagram;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.elk.core.data.ILayoutMetaDataProvider;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;

import com.eclipsesource.glsp.api.action.kind.RequestModelAction;
import com.eclipsesource.glsp.api.factory.ModelFactory;
import com.eclipsesource.glsp.api.utils.ModelOptions;
import com.eclipsesource.glsp.ecore.model.ClassNode;
import com.eclipsesource.glsp.ecore.model.EcoreEdge;
import com.eclipsesource.glsp.ecore.model.Icon;

import io.typefox.sprotty.api.Dimension;
import io.typefox.sprotty.api.LayoutOptions;
import io.typefox.sprotty.api.Point;
import io.typefox.sprotty.api.SCompartment;
import io.typefox.sprotty.api.SGraph;
import io.typefox.sprotty.api.SLabel;
import io.typefox.sprotty.api.SModelElement;
import io.typefox.sprotty.api.SModelRoot;
import io.typefox.sprotty.api.SNode;
import io.typefox.sprotty.layout.ElkLayoutEngine;

public class EcoreModelFactory implements ModelFactory {

	private static Logger LOGGER = Logger.getLogger(EcoreModelFactory.class);

	@Override
	public SModelRoot loadModel(RequestModelAction action) {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
		return loadModel(resourceSet, URI.createURI(action.getOptions().get(ModelOptions.SOURCE_URI)));
	}

	public SModelRoot loadModel(ResourceSet resourceSet, URI sourceURI) {
		SGraph result = new SGraph();
		result.setId("graph");
		result.setType("graph");
		result.setSize(new Dimension(10000, 8000));
		try {
			EcorePackage.eINSTANCE.eClass();
			Resource resource = resourceSet.createResource(sourceURI);
			resource.load(null);
			EObject eObject = resource.getContents().get(0);
			EPackage ePackage = (EPackage) eObject;
			fillGraph(result, ePackage);
		} catch (IOException e) {
			e.printStackTrace();
			LOGGER.error(e);
		}
		return result;
	}

	public void layoutModel(SModelRoot result, ILayoutMetaDataProvider layoutMetaDataProvider) {
		ElkLayoutEngine.initialize(layoutMetaDataProvider);
		EcoreLayoutEngine layoutEngine = new EcoreLayoutEngine();
		layoutEngine.layout(result);
	}

	private void fillGraph(SGraph sGraph, EPackage ePackage) {
		List<SModelElement> graphChildren = new ArrayList<SModelElement>();
		sGraph.setChildren(graphChildren);

		for (EClassifier eClassifier : ePackage.getEClassifiers()) {
			SNode node = createClassifierNode(eClassifier);
			graphChildren.add(node);
			if (eClassifier instanceof EClass) {
				// create attributes
				EClass eClass = (EClass) eClassifier;
				SCompartment attributeCompartment = new SCompartment();
				attributeCompartment.setId(node.getId() + "_attrs");
				attributeCompartment.setType("comp:comp");
				attributeCompartment.setLayout("vbox");
				attributeCompartment.setPosition(new Point(0, 0));
				List<SModelElement> attributes = new ArrayList<SModelElement>();
				LayoutOptions attributeCompartementLO = new LayoutOptions();
				attributeCompartementLO.setHAlign("left");
				attributeCompartment.setLayoutOptions(attributeCompartementLO);
				attributeCompartment.setChildren(attributes);
				node.getChildren().add(attributeCompartment);
				for (EAttribute eAttribute : eClass.getEAttributes()) {
					SLabel attribute = new SLabel();
					attribute.setId(node.getId() + "_" + eAttribute.getName());
					attribute.setType("label:text");
					attribute.setText(String.format(" - %s : %s", eAttribute.getName(),
							eAttribute.getEAttributeType().getName()));
					attributes.add(attribute);
				}
				for (EReference eReference : eClass.getEReferences()) {
					EcoreEdge reference = new EcoreEdge();
					reference.getCssClasses().add("ecore-edge");
					reference.setId(String.format("%s_%s_%s", eClass.getName(),
							eReference.getEReferenceType().getName(), eReference.getName()));
					String type = eReference.isContainment() ? "composition" : "aggregation";
					reference.getCssClasses().add(type);
					reference.setType("edge:" + type);
					reference.setSourceId(eClass.getName());
					reference.setTargetId(eReference.getEReferenceType().getName());
					reference.setMultiplicitySource("0..1");
					reference.setMultiplicityTarget(
							String.format("%s..%s", eReference.getLowerBound(), eReference.getUpperBound()));

					SLabel refName = new SLabel();
					refName.setId(reference.getId() + "_name");
					refName.setType("label:text");
					refName.setText(eReference.getName());
					reference.setChildren(Collections.singletonList(refName));

					graphChildren.add(reference);
				}
				for (EClass superClass : eClass.getESuperTypes()) {
					EcoreEdge reference = new EcoreEdge();
					reference.getCssClasses().add("ecore-edge");
					reference.getCssClasses().add("inheritance");
					reference.setId(String.format("%s_%s_parent", eClass.getName(), superClass.getName()));
					reference.setType("edge:inheritance");
					reference.setSourceId(eClass.getName());
					reference.setTargetId(superClass.getName());
					graphChildren.add(reference);
				}
			} else if (eClassifier instanceof EEnum) {
				// create attributes
				EEnum eEnum = (EEnum) eClassifier;
				SCompartment literalsCompartment = new SCompartment();
				LayoutOptions literalsCompartmentLO = new LayoutOptions();
				literalsCompartmentLO.setHAlign("left");
				literalsCompartment.setLayoutOptions(literalsCompartmentLO);
				literalsCompartment.setId(node.getId() + "_enums");
				literalsCompartment.setType("comp:comp");
				literalsCompartment.setLayout("vbox");
				List<SModelElement> literals = new ArrayList<SModelElement>();
				literalsCompartment.setChildren(literals);
				node.getChildren().add(literalsCompartment);

				for (EEnumLiteral eliteral : eEnum.getELiterals()) {
					SLabel literal = new SLabel();
					literal.setId(node.getId() + "_" + eliteral.getName());
					literal.setType("label:text");
					literal.setText(" - " + eliteral.getLiteral());
					literals.add(literal);
				}
			}
		}
	}

	private SNode createClassifierNode(EClassifier eClassifier) {
		ClassNode classNode = new ClassNode();
		classNode.setId(eClassifier.getName());
		classNode.setType("node:class");
		classNode.setLayout("vbox");
		classNode.setExpanded(true);
		classNode.getCssClasses().add("ecore-node");
		List<SModelElement> classChildren = new ArrayList<SModelElement>();
		classNode.setChildren(classChildren);
		classNode.setPosition(new Point(0, 0));

		// header
		SCompartment header = new SCompartment();
		header.setId(classNode.getId() + "_header");
		header.setType("comp:header");
		header.setLayout("hbox");
		List<SModelElement> header_children = new ArrayList<SModelElement>();
		header.setChildren(header_children);
		header.setPosition(new Point(0, 0));
		// icon with label
		Icon icon = new Icon();
		icon.setId(classNode.getId() + "_icon");
		icon.setType("icon");
		icon.setLayout("stack");
		LayoutOptions iconLO = new LayoutOptions();
		iconLO.setHAlign("center");
		iconLO.setResizeContainer(false);
		icon.setLayoutOptions(iconLO);

		SLabel iconLabel = new SLabel();
		iconLabel.setId(classNode.getId() + "_iconlabel");
		iconLabel.setType("label:icon");
		String iconLabelText = "C";
		if (eClassifier instanceof EClass) {
			EClass clazz = (EClass) eClassifier;
			if (clazz.isAbstract()) {
				iconLabelText = "A";
				classNode.getCssClasses().add("abstract");

			} else if (clazz.isInterface()) {
				iconLabelText = "I";
				classNode.getCssClasses().add("interface");
			}

		} else if (eClassifier instanceof EEnum) {
			iconLabelText = "E";
			classNode.getCssClasses().add("enum");
		}
		iconLabel.setText(iconLabelText);
		icon.setChildren(Collections.singletonList(iconLabel));
		header_children.add(icon);

		// label
		SLabel label = new SLabel();
		label.setId(classNode.getId() + "_classname");
		label.setType("label:heading");
		label.setText(eClassifier.getName());
		LayoutOptions labelLO = new LayoutOptions();
		labelLO.setHAlign("center");
		labelLO.setResizeContainer(false);
		label.setLayoutOptions(labelLO);
		header_children.add(label);

		// ExpandButton
//		SButton expand = new SButton();
//		expand.setId(classNode.getId()+"_expand");
//		expand.setType("button:expand");
//		header_children.add(expand);
		classChildren.add(header);

//		SCompartment linkCompartment = new SCompartment();
//		linkCompartment.setId(classNode.getId()+"_linkComp");
//		linkCompartment.setType("comp:comp");
//		linkCompartment.setLayout("vbox");
//		linkCompartment.setPosition(new Point(0, 0));
//		
//		Link link = new Link();
//		link.setId(classNode.getId()+"_link");
//		link.setType("link");
//		link.setText("Open");
//		link.setTarget("http://www.google.com");
//		linkCompartment.setChildren(Collections.singletonList(link));
//		classChildren.add(linkCompartment);
		return classNode;
	}
}
