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
public class ExplanationEntityIdentifier extends QanaryComponent {

	private static final String FILENAME_STORE_COMPUTED_ANNOTATIONS = "/queries/storeComputedAnnotations.rq";

	private static final Logger logger = LoggerFactory.getLogger(ExplanationEntityIdentifier.class);

	private final Pattern graphRegex = Pattern.compile("<\\S+>");
	private final Pattern componentRegex = Pattern.compile("<urn:qanary[^>]*>");

	private final String applicationName;

	public ExplanationEntityIdentifier(@Value("${spring.application.name}") final String applicationName) {
		this.applicationName = applicationName;

		QanaryTripleStoreConnector.guardNonEmptyFileFromResources(FILENAME_STORE_COMPUTED_ANNOTATIONS);
	}

	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) throws Exception {
		logger.info("process: {}", myQanaryMessage);

		// typical helpers
		QanaryUtils myQanaryUtils = this.getUtils();
		QanaryTripleStoreConnector connectorToQanaryTriplestore = myQanaryUtils.getQanaryTripleStoreConnector();

		// STEP 1: get the required data from the Qanary triplestore (the global process memory)

		QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion();


		// STEP 2: Compute new knowledge about the given question

		String question = myQanaryQuestion.getTextualRepresentation();

		Matcher matcher;
		matcher = graphRegex.matcher(question);
		matcher.find();
		String graphId = question.substring(matcher.start()+1,matcher.end()-1);
		matcher = componentRegex.matcher(question);
		matcher.find();
		String component = question.substring(matcher.start()+1,matcher.end()-1);

		logger.info("Found graph: {} and component: {}", graphId, component);

		// STEP 3: Insert newly computed data to triplestore

		QuerySolutionMap bindingsForUpdate = new QuerySolutionMap();
		// at least the variable GRAPH needs to be replaced by the outgraph as each query needs to be specific for the current process
		bindingsForUpdate.add("graph", ResourceFactory.createResource(myQanaryQuestion.getOutGraph().toASCIIString()));
		bindingsForUpdate.add("hasGraph", ResourceFactory.createResource(graphId));
		bindingsForUpdate.add("usedComponent", ResourceFactory.createResource(component));
		bindingsForUpdate.add("component", ResourceFactory.createPlainLiteral("urn:qanary:" + applicationName));

		String sparqlUpdateQuery = QanaryTripleStoreConnector.readFileFromResourcesWithMap(FILENAME_STORE_COMPUTED_ANNOTATIONS, bindingsForUpdate);
		logger.info("generated SPARQL UPDATE query: {}", sparqlUpdateQuery);
		connectorToQanaryTriplestore.update(sparqlUpdateQuery);

		return myQanaryMessage;
	}
}


