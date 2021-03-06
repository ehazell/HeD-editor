package edu.asu.sharpc2b.hed.impl;

import edu.asu.sharpc2b.hed.ArtifactRepository;
import edu.asu.sharpc2b.hed.RepositoryFactory;
import edu.asu.sharpc2b.hed.api.ArtifactStore;
import edu.asu.sharpc2b.hed.api.DomainModel;
import edu.asu.sharpc2b.hed.api.EditorCore;
import edu.asu.sharpc2b.prr_sharp.HeDKnowledgeDocument;
import edu.asu.sharpc2b.transform.HeD2OwlDumper;
import org.drools.io.ResourceFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EditorCoreImpl implements EditorCore, DomainModel, ArtifactStore {

    private ConcurrentMap<String,HeDArtifactData> artifacts;
    private String currentArtifactId;

    private ArtifactRepository knowledgeRepo = RepositoryFactory.getRepository( RepositoryFactory.REPOSITORY.STANBOL );
    

    private static EditorCoreImpl instance = new EditorCoreImpl();

    public static EditorCore getInstance() {
        return instance;
    }

    protected EditorCoreImpl() {
        artifacts = new ConcurrentHashMap<String,HeDArtifactData>();
        
    }

    
    public String getCurrentArtifactId() {
        return currentArtifactId;
    }
    
    public HeDArtifactData getCurrentArtifact() {
        return artifacts.get( currentArtifactId ); 
    }

    void setCurrentArtifactId( String currentArtifactId ) {
        this.currentArtifactId = currentArtifactId;
    }






    @Override
    public Map<String,String> getNamedExpressions() {
        return getCurrentArtifact().getNamedExpressions();
    }

    @Override
    public byte[] getNamedExpression( String exprId ) {
        return getCurrentArtifact().getNamedExpression( exprId ).getDoxBytes();
    }

    @Override
    public boolean deleteNamedExpression( String exprId ) {
        System.out.println( "Deleting .. " + exprId );
        boolean hasExpr = getCurrentArtifact().getNamedExpressions().containsKey( exprId );
        if ( hasExpr ) {
            System.out.println( "Deleted .. " + exprId );
            getCurrentArtifact().getNamedExpressions().remove( exprId );
            return true;
        } else {
            return false;
        }
    }
    @Override
    public String cloneNamedExpression( String exprId ) {
        HeDNamedExpression expr = getCurrentArtifact().getNamedExpression( exprId );
        String id = UUID.randomUUID().toString();
        getCurrentArtifact().updateNamedExpression( id, expr.getName(), expr.getDoxBytes() );
        return id;
    }

    @Override
    public boolean updateNamedExpression( String exprId, String exprName, byte[] doxBytes ) {
        Document dox = null;
        try {
            DocumentBuilder doxBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doxBuilder.parse( new ByteArrayInputStream( doxBytes ) );
        } catch ( Exception e ) {
            e.printStackTrace();
            return false;
        }

        return getCurrentArtifact().updateNamedExpression( exprId, exprName, doxBytes );
    }


    
    
    


    private Map<String,String> domKlasses;
    private Map<String,String> domProptis;

    @Override
    public Map<String, String> getDomainClasses() {
        if ( domKlasses == null ) {
            domKlasses = new HashMap<String,String>();
            try {
                OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-core.owl" ) );
                manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-ext.owl" ) );
                manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/expr-core.owl" ) );
                OWLOntology domain = manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/domain_models/domain-vmr.ofn" ) );
                OWLDataFactory odf = domain.getOWLOntologyManager().getOWLDataFactory();
                for ( OWLNamedIndividual ind : domain.getIndividualsInSignature() ) {
                    if ( ind.getTypes( domain ).contains( odf.getOWLClass( IRI.create( "http://asu.edu/sharpc2b/ops#DomainClass" ) ) ) )  {
                        if ( ! isDatatype( ind.getIRI() ) && ! isAbstract( ind.getIRI() ) ) {
                            domKlasses.put( ind.getIRI().toString(), ind.getIRI().getFragment() );
                        }
                    }
                }
            } catch ( OWLOntologyCreationException e ) {
                e.printStackTrace();
            }
            return domKlasses;

        } else {
            return domKlasses;
        }
    }

    private boolean isAbstract( IRI iri ) {
        //TODO hack!
//        return iri.getFragment().endsWith( "Base" );
        return false;
    }

    private boolean isDatatype( IRI iri ) {
        //TODO hack!
        return iri.getFragment().toUpperCase().equals( iri.getFragment() ) || iri.getFragment().contains( "." );
    }

    @Override
    public Map<String, String> getDomainProperties() {
        if ( domProptis == null ) {
            domProptis = new HashMap<String,String>();
            try {
                OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
                manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-core.owl" ) );
                manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-ext.owl" ) );
                manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/expr-core.owl" ) );
                OWLOntology domain = manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/domain_models/domain-vmr.ofn" ) );
                OWLDataFactory odf = domain.getOWLOntologyManager().getOWLDataFactory();
                for ( OWLNamedIndividual ind : domain.getIndividualsInSignature() ) {
                    if ( ind.getTypes( domain ).contains( odf.getOWLClass( IRI.create( "http://asu.edu/sharpc2b/ops#DomainProperty"  ) ) ) ) {
                        Set<OWLIndividual> values = ind.getObjectPropertyValues( odf.getOWLObjectProperty( IRI.create( "http://asu.edu/sharpc2b/skos-ext#partOf" ) ), domain );
                        for ( OWLIndividual val : values ) {
                            if ( val.isNamed() && getDomainClasses().containsKey( ((OWLNamedIndividual) val ).getIRI().toString()) ) {
                                domProptis.put( ind.getIRI().toString(), ( (OWLNamedIndividual) val ).getIRI().getFragment() + "::" + ind.getIRI().getFragment() );
                            }
                        }
                    }
                }
            } catch ( OWLOntologyCreationException e ) {
                e.printStackTrace();
            }
            return domProptis;

        } else {
            return domProptis;
        }
    }

    @Override
    public Map<String, String> getDomainProperties( String klassId ) {
        return Collections.emptyMap();
    }




    private OWLOntology templates;

    private void loadTemplateOntology() {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            templates = manager.createOntology( IRI.create( "http://asu.edu/sharpc2b/templates/data" ) );
            manager = templates.getOWLOntologyManager();
            manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-core.owl" ) );
            manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-ext.owl" ) );
            manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/expr-core.owl" ) );
            manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/sharp_operators.ofn" ) );
            manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/domain_models/domain-vmr.ofn" ) );
            manager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/templates/template_schema.owl" ) );
            File dataDir = new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/templates/data" );
            for ( File tempFile : dataDir.listFiles( new FilenameFilter() {
                @Override
                public boolean accept( File dir, String name ) {
                    return name.endsWith( ".owl" );
                }
            }) ) {
                OWLOntologyManager subManager = OWLManager.createOWLOntologyManager();
                subManager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-core.owl" ) );
                subManager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/skos-ext.owl" ) );
                subManager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/expr-core.owl" ) );
                subManager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/editor_models/sharp_operators.ofn" ) );
                subManager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/domain_models/domain-vmr.ofn" ) );
                subManager.loadOntologyFromOntologyDocument( new File( "/home/davide/Projects/Git/HeD-editor/sharp-editor/editor-models/hed-ontologies/src/main/resources/ontologies/templates/template_schema.owl" ) );
                OWLOntology temp = subManager.loadOntologyFromOntologyDocument( tempFile );
                manager.addAxioms( templates, temp.getAxioms() );
            }

        } catch ( OWLOntologyCreationException e ) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }


    @Override
    public Set<String> getTemplateIds( String category ) {
        System.out.println( "Called core getTemplateIds with " + category );
        if ( templates == null ) {
            loadTemplateOntology();
        }
        OWLDataFactory odf = templates.getOWLOntologyManager().getOWLDataFactory();
        Set<OWLNamedIndividual> individuals = templates.getIndividualsInSignature( true );
        Set<String> individualIds = new HashSet<String>();
        for ( OWLNamedIndividual ind : individuals ) {
            if ( ind.getTypes( templates.getImportsClosure() ).contains( odf.getOWLClass( templateIRI( "Template" ) ) ) ) {
                Set<OWLLiteral> cats = ind.getDataPropertyValues( odf.getOWLDataProperty( templateIRI( "category" ) ), templates );
                if ( category != null && ! category.isEmpty() ) {
                    if ( cats.contains( odf.getOWLLiteral( category ) ) ) {
                        individualIds.add( ind.getIRI().toString() );
                    }
                } else {
                    individualIds.add( ind.getIRI().toString() );
                }
            }
        }
        System.out.println( "Retrieved individual ids " + individualIds );
        return individualIds;
    }

    private IRI templateIRI( String s ) {
        return IRI.create( "http://asu.edu/sharpc2b/templates#" + s );
    }

    private IRI templateDataIRI( String s ) {
        return IRI.create( "http://asu.edu/sharpc2b/templates/data#" + s );
    }


    @Override
    public Map<String, Object> getTemplateInfo( String templateId ) {
        if ( templates == null ) {
            loadTemplateOntology();
        }
        System.out.println( "Requesting info about " + templateId );
        OWLDataFactory odf = templates.getOWLOntologyManager().getOWLDataFactory();
        Map<String,Object> info = new HashMap<String,Object>();

        OWLNamedIndividual ind = odf.getOWLNamedIndividual( IRI.create( templateId ) );

        info.put( "templateId", templateId );
        info.put( "category", getDataPropertyValue( ind, "category", odf ) );
        info.put( "name", getDataPropertyValue( ind, "name", odf ) );
        info.put( "group", getDataPropertyValue( ind, "group", odf ) );
        info.put( "description", getDataPropertyValue( ind, "description", odf ) );
        info.put( "example", getDataPropertyValue( ind, "example", odf ) );

        List<String> params = getObjectPropertyValues( ind, "hasParameter", odf );
        info.put( "parameterIds", params );

        LinkedHashMap<String,Map<String,Object>> paramData = new LinkedHashMap<String,Map<String,Object>>();
        for ( String pid : params ) {
            Map<String,Object> details = new HashMap<String,Object>();
            OWLNamedIndividual param = odf.getOWLNamedIndividual( IRI.create( pid ) );

            details.put( "key", pid );
            String name = getDataPropertyValue( param, "name", odf );
            details.put( "name", name );
            details.put( "label", name );
            details.put( "description", getDataPropertyValue( param, "description", odf ) );
            details.put( "typeName", getDataPropertyValue( param, "typeName", odf ) );
            details.put( "expressions", new ArrayList( getNamedExpressions().keySet() ) );

            paramData.put( pid, details );
        }
        info.put( "parameterData", paramData );

        System.out.println( "Retrieved info " + info );
        return info;
    }

    private String getDataPropertyValue( OWLNamedIndividual ind, String propName, OWLDataFactory odf ) {
        Set<OWLLiteral> values = ind.getDataPropertyValues( odf.getOWLDataProperty( templateIRI( propName ) ), templates );
        if ( values.isEmpty() ) {
            return null;
        } else {
            return values.iterator().next().getLiteral();
        }
    }

    private List<String> getDataPropertyValues( OWLNamedIndividual ind, String propName, OWLDataFactory odf ) {
        Set<OWLLiteral> values = ind.getDataPropertyValues( odf.getOWLDataProperty( templateIRI( propName ) ), templates );
        if ( values.isEmpty() ) {
            return null;
        } else {
            ArrayList<String> literals = new ArrayList<String>( values.size() );
            for ( OWLLiteral lit : values ) {
                literals.add( lit.getLiteral() );
            }
            return literals;
        }
    }

    private List<String> getObjectPropertyValues( OWLNamedIndividual ind, String propName, OWLDataFactory odf ) {
        Set<OWLIndividual> values = ind.getObjectPropertyValues( odf.getOWLObjectProperty( templateIRI( propName ) ), templates );
        if ( values.isEmpty() ) {
            return null;
        } else {
            ArrayList<String> targets = new ArrayList<String>( values.size() );
            for ( OWLIndividual tgt : values ) {
                targets.add( tgt.toStringID() );
            }
            return targets;
        }
    }


    public String instantiateTemplate( String tId, String name, Map<String,Map<String,Object>> parameterValues ) {
        System.out.println( "Core init " + tId + " with name " + name + " using " + parameterValues );
        if ( templates == null ) {
            loadTemplateOntology();
        }

        OWLDataFactory odf = templates.getOWLOntologyManager().getOWLDataFactory();
        OWLNamedIndividual root = odf.getOWLNamedIndividual( templateDataIRI( tId ) );

        List<String> exprRoots = getObjectPropertyValues( root, "hasIncarnation", odf );
        for ( String exprName : exprRoots ) {
            String expression = buildExpression( exprName, odf );
            this.getCurrentArtifact().updateNamedExpression( exprName, exprName, expression.getBytes() );
        }

        return null;
    }

    private String buildExpression( String exprName, OWLDataFactory odf ) {
        try {
            OWLNamedIndividual root = odf.getOWLNamedIndividual( IRI.create( exprName ) );
            Document dox = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element rootElem = dox.createElement( "xml" );
            dox.appendChild( rootElem );
            visitNode( root, rootElem, dox, odf );


            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty( OutputKeys.INDENT, "yes");
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(dox);
            transformer.transform(source, result);
            String xmlString = result.getWriter().toString();
            System.out.println(xmlString);

            return "";
        } catch ( Exception e ) {
            e.printStackTrace();
        }
        return null;
    }

    private void visitNode( OWLIndividual root, Element rootElem, Document dox, OWLDataFactory odf ) {
        OWLClassExpression type = root.getTypes( templates ).iterator().next();
        Element block = dox.createElement( "block" );
            block.setAttribute( "type", type.asOWLClass().toStringID() );
            visitLinks( root, rootElem, dox, odf );
        rootElem.appendChild( block );
    }

    private void visitLinks( OWLIndividual root, Element rootElem, Document dox, OWLDataFactory odf ) {
        //To change body of created methods use File | Settings | File Templates.
    }





    @Override
    public String createArtifact() {

        HeDArtifactData artifact = new HeDArtifactData();
        currentArtifactId = artifact.getArtifactId();

        InputStream stream = new ByteArrayInputStream( "test".getBytes() );

        artifacts.put( currentArtifactId, artifact );

        knowledgeRepo.createArtifact( currentArtifactId, artifact.getTitle(), stream );

        return currentArtifactId;
    }


    @Override
    public List<String> getAvailableArtifacts() {
        return knowledgeRepo.getAvailableArtifacts();
    }

    @Override
    public String importFromStream( byte[] hedStream ) {
        try {

            org.drools.io.Resource file = ResourceFactory.newFileResource( "/home/davide/Projects/Git/HeD-editor/sharp-editor/import-export/src/test/resources/DiabetesReminderRule.xml" );
            hedStream = new byte[ file.getInputStream().available() ];
            file.getInputStream().read( hedStream );

            System.out.println( "Received import message, bytes avai " + hedStream.length );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ByteArrayInputStream bais = new ByteArrayInputStream( hedStream );
            new HeD2OwlDumper().compile( bais, baos );
            byte[] owlBytes = baos.toByteArray();

            HeDKnowledgeDocument knowledgeDocument = new ModelManagerOwlAPIHermit().loadRootThingFromOntologyStream( owlBytes );
            HeDArtifactData artifact = new HeDArtifactData( knowledgeDocument );

            currentArtifactId = artifact.getArtifactId();

            artifacts.put( currentArtifactId, artifact );

            knowledgeRepo.createArtifact( currentArtifactId, artifact.getTitle(), new ByteArrayInputStream( owlBytes ) );

            baos.close();

            return currentArtifactId;
        } catch ( IOException e ) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String cloneArtifact( String id ) {
        return knowledgeRepo.cloneArtifact( id );
    }

    @Override
    public String openArtifact( String id ) {
        System.out.println( "Core opens artifact " + id );

        try {
            InputStream in = knowledgeRepo.loadArtifact( id );
            byte[] data = new byte[ in.available() ];
            in.read( data );

            System.out.println( "Found bytes " + data.length );
            System.out.println( "Found content " + new String( data ) );

            HeDKnowledgeDocument knowledgeDocument = new ModelManagerOwlAPIHermit().loadRootThingFromOntologyStream( data );
            HeDArtifactData artifact = new HeDArtifactData( knowledgeDocument );

            currentArtifactId = artifact.getArtifactId();

            artifacts.put( currentArtifactId, artifact );
            in.close();
            System.out.println( "Opened artifact with title " + knowledgeDocument.getTitle() );
            return id;
        } catch ( IOException ioe ) {
            ioe.printStackTrace();
        }
        return null;
    }

    @Override
    public String snapshotArtifact( String id ) {
        return knowledgeRepo.snapshotArtifact( id );
    }

    @Override
    public String saveArtifact( String id ) {
        return knowledgeRepo.saveArtifact( id );
    }

    @Override
    public String exportArtifact( String id ) {
        return "";
    }

    @Override
    public String closeArtifact() {
        String lastId = currentArtifactId;
        this.artifacts.remove( lastId );
        this.currentArtifactId = null;
        return lastId;
    }

    @Override
    public String deleteArtifact( String id ) {
        this.artifacts.remove( id );
        this.currentArtifactId = null;
        return knowledgeRepo.deleteArtifact( id );
    }


}
