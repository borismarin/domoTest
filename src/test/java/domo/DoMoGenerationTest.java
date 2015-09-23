package domo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.bind.Unmarshaller;

import org.FooML.model.Bar;
import org.FooML.model.Base;
import org.FooML.model.Baz;
import org.FooML.model.Foo;
import org.FooML.model.FooML;
import org.junit.Before;
import org.junit.Test;
import org.lemsml.model.compiler.LEMSCompilerFrontend;
import org.lemsml.model.compiler.semantic.LEMSSemanticAnalyser;
import org.lemsml.model.exceptions.LEMSCompilerException;
import org.lemsml.model.extended.Component;
import org.lemsml.model.extended.Lems;

import com.google.common.base.Charsets;
import com.google.common.io.Files;


public class DoMoGenerationTest {

	private JAXBContext jaxbContext;
	private FooML fooModel;
	private Lems domainDefs;

	@Before
	public void setUp() throws Throwable {
		domainDefs = new LEMSCompilerFrontend(getLocalFile("/lems/FooML.xml")) .generateLEMSDocument();


		// We then want to unmarshall a DS model (defined in xml) using the
		// generated (hybrid domain/lems) objects (of which we have literal
		// pregenerated examples FooML.java Foo.java, etc)
		File model = getLocalFile("/foo.xml");

		jaxbContext = JAXBContext.newInstance("org.FooML.model");
//		genSchema(jaxbContext); //TODO: see comment on method
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		fooModel = (FooML) jaxbUnmarshaller.unmarshal(model);
		fooModel.getComponentTypes().addAll(domainDefs.getComponentTypes());
		new LEMSSemanticAnalyser(fooModel).analyse();
	}

	@Test
	public void testGeneration(){

		assertEquals(6, domainDefs.getComponentTypes().size());
	}

	@Test
	public void testTypes() throws LEMSCompilerException {
		assertEquals(1, fooModel.getFoos().size());
		assertEquals(0, fooModel.getBars().size());
		assertEquals(6, fooModel.getAllOfType(Bar.class).size());
		assertEquals(5, fooModel.getAllOfType(Baz.class).size());
		assertEquals(10, fooModel.getAllOfType(Base.class).size());

		Foo foo0 = (Foo) fooModel.getComponentById("foo0");

		assertTrue(fooModel.getAllOfType(Foo.class).contains(foo0));
		assertEquals(2, foo0.getFooBazs().size());
		assertEquals("10", foo0.getFooBar().getParameterValue("pBar"));
	}

	@Test
	public void testEvaluation() throws LEMSCompilerException {

		Component foo0 = fooModel.getComponentById("foo0");
		Component barInFoo0 = fooModel.getComponentById("fooBar");

		Double pBar = Double.valueOf(fooModel.getFoos().get(0).getFooBar().getPBar());
		assertEquals(pBar, foo0
							.getChildren()
							.get(0)
							.getScope()
							.evaluate("pBar").getValue().doubleValue(), 1e-12);
		assertEquals(0.1, barInFoo0
							.getScope()
							.evaluate("dpBar").getValue().doubleValue(), 1e-12);

		//testing synch
		//changing par via lems api
		foo0.withParameterValue("pFoo", "3");
		assertEquals(0.3, barInFoo0
							.getScope()
							.evaluate("dpBar").getValue().doubleValue(), 1e-12);

		//changing par via domain api
		((Foo) foo0).setPFoo("4");

		assertEquals("4", ((Foo) foo0).getPFoo());
		assertEquals(0.4, barInFoo0
							.getScope()
							.evaluate("dpBar").getValue().doubleValue(), 1e-12);
	}

	@Test
	public void testMarshalling()
			throws JAXBException, PropertyException, IOException {

		File tmpFile = File.createTempFile("test", ".xml");
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

		fooModel.getComponentTypes().clear();
		eraseTypes(fooModel.getComponents()); //TODO: extremely ugly hack

		marshaller.marshal(fooModel, tmpFile);
		System.out.println(Files.toString(tmpFile, Charsets.UTF_8));
	}

	//TODO: argh! @XmlTransient in ext.Comp isn't overriding type from (on-ext)Comp
	void eraseTypes(List<Component> list){
		for(Component comp : list){
			eraseTypes(comp.getComponent());
			comp.withType(null);
		}

	}

	protected File getLocalFile(String fname) {
		return new File(getClass().getResource(fname).getFile());
	}

}
