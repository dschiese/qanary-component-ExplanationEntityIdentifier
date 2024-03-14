package eu.wdaqua.qanary.explanationentityidentifier;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.commons.QanaryQuestion;
import eu.wdaqua.qanary.commons.QanaryUtils;
import eu.wdaqua.qanary.commons.triplestoreconnectors.QanaryTripleStoreConnector;
import eu.wdaqua.qanary.component.QanaryComponent;
import eu.wdaqua.qanary.exceptions.SparqlQueryFailed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class ExplanationEntityIdentifier extends QanaryComponent {

	private static final String FILENAME_FETCH_REQUIRED_ANNOTATIONS = "/queries/fetchRequiredAnnotations.rq";

	private static final String FILENAME_STORE_COMPUTED_ANNOTATIONS = "/queries/storeComputedAnnotations.rq";

	private static final Logger logger = LoggerFactory.getLogger(ExplanationEntityIdentifier.class);

	private final Pattern graphRegex = Pattern.compile("<\\S+>");
	private final Pattern componentRegex = Pattern.compile("<urn:qanary[^>]*>");

	private final String applicationName;

	public ExplanationEntityIdentifier(@Value("${spring.application.name}") final String applicationName) {
		this.applicationName = applicationName;

		//    QanaryTripleStoreConnector.guardNonEmptyFileFromResources(FILENAME_FETCH_REQUIRED_ANNOTATIONS);
		QanaryTripleStoreConnector.guardNonEmptyFileFromResources(FILENAME_STORE_COMPUTED_ANNOTATIONS);
	}

	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		// typical helpers
		QanaryUtils myQanaryUtils = this.getUtils();
		QanaryTripleStoreConnector connectorToQanaryTriplestore = myQanaryUtils.getQanaryTripleStoreConnector();

		// --------------------------------------------------------------------
		// STEP 1: get the required data from the Qanary triplestore (the global process memory)
		// --------------------------------------------------------------------
		// if required, then fetch the origin question (here the question is a
		// textual/String question)
		QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion();


		// --------------------------------------------------------------------
		// STEP 2: compute new knowledge about the given question
		// --------------------------------------------------------------------
		// TODO: implement the custom code for your component

		String question = myQanaryQuestion.getTextualRepresentation();

		Matcher matcher;
		matcher = graphRegex.matcher(question);
		matcher.find();
		String graphId = question.substring(matcher.start()+1,matcher.end()-1);
		matcher = componentRegex.matcher(question);
		matcher.find();
		String component = question.substring(matcher.start()+1,matcher.end()-1);

		logger.info("Found graph: {} and component: {}", graphId, component);

		// --------------------------------------------------------------------
		// STEP 3: store computed knowledge about the given question into the Qanary triplestore
		// (the global process memory)
		// --------------------------------------------------------------------
		logger.info("store data in graph {} of Qanary triplestore endpoint {}", //
				myQanaryMessage.getValues().get(myQanaryMessage.getOutGraph()), //
				myQanaryMessage.getValues().get(myQanaryMessage.getEndpoint()));

		// push the new data to the Qanary triplestore

		// TODO: define the SPARQL query fetch the data that your component requires
		QuerySolutionMap bindingsForUpdate = new QuerySolutionMap();
		// at least the variable GRAPH needs to be replaced by the outgraph as each query needs to be specific for the current process
		bindingsForUpdate.add("graph", ResourceFactory.createResource(myQanaryQuestion.getOutGraph().toASCIIString()));
		bindingsForUpdate.add("hasGraph", ResourceFactory.createResource(graphId));
		bindingsForUpdate.add("usedComponent", ResourceFactory.createResource(component));
		bindingsForUpdate.add("component", ResourceFactory.createPlainLiteral("urn:qanary:" + applicationName));

		// TODO: define your SPARQL UPDATE query in the mentioned file
		String sparqlUpdateQuery = QanaryTripleStoreConnector.readFileFromResourcesWithMap(FILENAME_STORE_COMPUTED_ANNOTATIONS, bindingsForUpdate);
		logger.info("generated SPARQL UPDATE query: {}", sparqlUpdateQuery);
		connectorToQanaryTriplestore.update(sparqlUpdateQuery);

		return myQanaryMessage;
	}
}


