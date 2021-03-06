package edu.asu.sharpc2b.transform;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.coode.owlapi.turtle.TurtleOntologyFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: rk
 */
    public class SharpOperators
{

//    static final File defaultExcelFile = FileUtil
//            .getFileInProjectDir( "/model-transform/src/main/resources/SharpOperators.xlsx" );


    static String typesBaseIRI = IriUtil.sharpEditorIRI( "ops" ).toString() + "#";

    static Map<String, IRI> typeNameIriMap = new HashMap<String, IRI>();

    static Map<String, String> typeNameMap = new HashMap<String, String>();

    static Map<String, IRI> typeExpressionNameMap = new HashMap<String, IRI>();

    static
    {
        initTypeNameMap();
        initTypeNameIriMap();
        initTypeExpressionNameMap();
    }

    static final int ARITY_NARY = -2;

    static final int ARITY_LIST = -1;

    static final int ARITY_NULLARY = 0;

    static final int ARITY_UNARY = 1;

    static final int ARITY_BINARY = 2;

    static final int ARITY_TERNARY = 3;

    //===============================================================================================

    private OWLOntologyManager oom;

    private OWLDataFactory odf;

//    private PrefixOWLOntologyFormat oFormat;

    private OWLOntology ont;

    private String outputOntBaseIRI;

    //===============================================================================================

    public SharpOperators ()
    {
        super();
    }

    //===============================================================================================

    public void addSharpOperators (final File excelFile,
                                   final OWLOntology ontology)
    {
        this.ont = ontology;
        setupOntologyManager( ont );
        addImports();
        this.outputOntBaseIRI = ont.getOntologyID().getOntologyIRI().toString() + "#";

        addMissingCommonAxioms();

        Sheet sheet = getExcelOperatorsSheet( excelFile );

        int lastRowNum = sheet.getLastRowNum();

        /*
         * Stores the operator name from the previous row of the spreadsheet. Needed to determine if same
          * operator name is used in multiple rows, in which case it needs a unique name to be created
          * for it.
          */
        String lastOperatorName = "NONE";

        /* skip the first row -- contains column names */

        for (int rowNum = 1; rowNum <= lastRowNum; rowNum++)
        {
            Row row = sheet.getRow( rowNum );
            int colOperatorName = 0;

            String operatorName = row.getCell( colOperatorName ).getStringCellValue();

            String nextOperatorName;
            if (rowNum < lastRowNum)
            {
                nextOperatorName = sheet.getRow( rowNum + 1 ).getCell( colOperatorName )
                        .getStringCellValue();
            }
            else
            {
                nextOperatorName = "NONE";
            }

            boolean overloadedName = operatorName.equals( lastOperatorName ) ||
                    operatorName.equals( nextOperatorName );

            processOneRow( row, overloadedName );

            /* setup for next iteration */
            lastOperatorName = operatorName;
        }
    }

    private void processOneRow (final Row row,
                                final boolean isOverloadedName)
    {
        /* set up column indexes for sheet columns we care about */
        int i = 0;
        int colOperatorName = i++;
        int colNumArgs = i++;
        int colResultType = i++;
        int colOperand1Type = i++;
        int colOperand2Type = i++;
        int colOperand3Type = i++;

        String opNameFromConfig = row.getCell( colOperatorName ).getStringCellValue();
//            String numArgs = row.getCell( colNumArgs ).getStringCellValue();
        String numArgs = row.getCell( colNumArgs ).toString();
        String resultTypeName = row.getCell( colResultType ).getStringCellValue();
        String op1TypeName = row.getCell( colOperand1Type ).getStringCellValue();

        boolean isNary = numArgs.equals( "n" );
        boolean isListArg = numArgs.equals( "a" );
        boolean hasArity = !(isNary || isListArg);

        int arity;
        {
            if (numArgs.equals( "n" ))
            {
                arity = -1;
            }
            else if (numArgs.equals( "a" ))
            {
                arity = -1;
            }
            else if (numArgs.equals( "1.0" ))
            {
                arity = 1;
            }
            else if (numArgs.equals( "2.0" ))
            {
                arity = 2;
            }
            else if (numArgs.equals( "3.0" ))
            {
                arity = 3;
            }
            else
            {
                throw new RuntimeException( "Encountered bad value for number of operands: " + numArgs );
            }
        }

            /* only needed for arity = 2 or 3 */
        String op2TypeName = 2 <= arity ? row.getCell( colOperand2Type ).getStringCellValue() : null;
        String op3TypeName = 3 <= arity ? row.getCell( colOperand3Type ).getStringCellValue() : null;

            /* At this point, have all the values from the spreadsheet row. */

            /*
             * If this operator name is used in more than one row,
             * need to create a compound name, such as "PlusInteger" instead of just "Plus".
             */

//            boolean isOverloadedName = opNameFromConfig .equals(  lastOperatorName )||
//                    opNameFromConfig .equals(  nextOperatorName);
        final String opName;
        opName = isOverloadedName ? (opNameFromConfig + typeNameMap.get( op1TypeName )) : opNameFromConfig;

        defineOperatorIndividual( opName, opNameFromConfig, resultTypeName, arity, op1TypeName, op2TypeName,
                                  op3TypeName );


        defineOperatorExpressionClass( opName, opNameFromConfig, resultTypeName, arity, op1TypeName,
                                       op2TypeName, op3TypeName );
    }

    /**
     * Create Individual to define the Operator.
     */
    private void defineOperatorIndividual (final String opName,
                                           final String opNameFromConfig,
                                           final String resultTypeName,
                                           final int arity,
                                           final String op1TypeName,
                                           final String op2TypeName,
                                           final String op3TypeName)
    {
    /* The OWL Individuals for the new Operator, and for the operand types and return type. */

        OWLNamedIndividual operator = odf.getOWLNamedIndividual( outputIRI( opName ) );
        OWLNamedIndividual operatorRepresentation = odf.getOWLNamedIndividual( outputIRI( opName + "Code" ) );
        OWLNamedIndividual resultType = getOpsTypeIndividual( resultTypeName );
        OWLNamedIndividual operand1Type = getOpsTypeIndividual( op1TypeName );
        OWLNamedIndividual operand2Type = 2 <= arity ? getOpsTypeIndividual( op2TypeName ) : null;
        OWLNamedIndividual operand3Type = 3 <= arity ? getOpsTypeIndividual( op3TypeName ) : null;

        addAxiom( odf.getOWLDeclarationAxiom( operator ) );
        addAxiom( odf.getOWLDeclarationAxiom( operatorRepresentation ) );

            /* assert rdf:type */
        addOperatorType( operator, arity );
        addAxiom( odf.getOWLClassAssertionAxiom( odf.getOWLClass( IriUtil.opsIRI( "OperatorConceptCode" ) ), operatorRepresentation ) );

            /* assert skos:notation */
        OWLDataProperty skosNotation = odf
                .getOWLDataProperty( IRI.create( "http://www.w3.org/2004/02/skos/core#notation" ) );
        OWLObjectProperty denotedBy = odf
                .getOWLObjectProperty( IRI.create( "http://asu.edu/sharpc2b/skos-ext#conceptDenotedBy" ) );
        OWLObjectProperty denotes = odf
                .getOWLObjectProperty( IRI.create( "http://asu.edu/sharpc2b/skos-ext#denotesConcept" ) );

        addAxiom( odf.getOWLDataPropertyAssertionAxiom( skosNotation, operatorRepresentation, opNameFromConfig ) );

            /* assert ops:code */
        OWLDataProperty conceptCode = odf.getOWLDataProperty( IriUtil.skosExtIRI( "code" ) );

        addAxiom( odf.getOWLObjectPropertyAssertionAxiom( denotedBy, operator, operatorRepresentation ) );
        addAxiom( odf.getOWLObjectPropertyAssertionAxiom( denotes, operatorRepresentation, operator ) );
        addAxiom( odf.getOWLDataPropertyAssertionAxiom( conceptCode, operatorRepresentation, opNameFromConfig ) );

            /* assert operator return Type */
        OWLObjectProperty resultTypeRelationship = odf
                .getOWLObjectProperty(  IriUtil.opsIRI( "evaluatesAs" ) );
        addAxiom( odf.getOWLObjectPropertyAssertionAxiom( resultTypeRelationship, operator, resultType ) );

        assertOperandTypes( arity, operator, operand1Type, operand2Type, operand3Type );

    }

    /**
     * Create Expression Class
     */
    private void defineOperatorExpressionClass (final String opName,
                                                final String opNameFromConfig,
                                                final String resultTypeName,
                                                final int arity,
                                                final String op1TypeName,
                                                final String op2TypeName,
                                                final String op3TypeName)
    {
//        final String opNameFromConfig = ; final boolean nary = ; final boolean listArg = ;
//
//        final boolean hasArity = ; final OWLDataProperty skosNotation = ;

        //            OWLClass exprClass = odf.getOWLClass( outputIRI( opName ) );
        OWLClass exprClass = odf.getOWLClass( outputIRI( opName + "Expression" ) );
        OWLClass superClass = getExpressionTypeClass( resultTypeName );

            /* point to superclass */
        addAxiom( odf.getOWLSubClassOfAxiom( exprClass, superClass ) );

            /*
             * Define the Expression OWLClass that defines the proper combination of operator and operand
             * types.
             */
        {
                /* Collect the conditions to AND together into a Collection. */

            final Set<OWLClassExpression> requirements = new HashSet<OWLClassExpression>();
            final Set<OWLClassExpression> closures = new HashSet<OWLClassExpression>();
            final Set<OWLClassExpression> disjoints = new HashSet<OWLClassExpression>();

            disjoints.add( odf.getOWLClass( IriUtil.skosIRI( "Collection" ) ) );
            disjoints.add( odf.getOWLClass( IriUtil.skosIRI( "ConceptScheme" ) ) );
            disjoints.add( odf.getOWLClass( IriUtil.skosIRI( "Concept" ) ) );
            disjoints.add( odf.getOWLClass( IriUtil.opsIRI( "Variable" ) ) );
            disjoints.add( exprClass );

            addAxiom( odf.getOWLDisjointClassesAxiom( disjoints ) );

            OWLObjectProperty hasOperator = odf.getOWLObjectProperty( IriUtil.opsIRI( "hasOperator" ) );
            OWLObjectProperty hasOperand = odf.getOWLObjectProperty( IriUtil.opsIRI( "hasOperand" ) );
            OWLObjectProperty hasOperand1 = odf.getOWLObjectProperty( IriUtil.opsIRI( "firstOperand" ) );
            OWLObjectProperty hasOperand2 = odf.getOWLObjectProperty( IriUtil.opsIRI( "secondOperand" ) );
            OWLObjectProperty hasOperand3 = odf.getOWLObjectProperty( IriUtil.opsIRI( "thirdOperand" ) );

            OWLClass masterParent = odf.getOWLClass( IriUtil.opsIRI( "OperatorExpression" ) );

            addAxiom( odf.getOWLSubClassOfAxiom( exprClass, masterParent ) );

                /* it is an Expression */
            requirements.add( masterParent );
//            requirements.add( odf.getOWLObjectHasValue( hasOperator, operator ) );

                /* AND has operator with the specified skos:notation */
            OWLDataProperty skosNotation = odf
                    .getOWLDataProperty( IRI.create( "http://www.w3.org/2004/02/skos/core#notation" ) );
            OWLObjectProperty denotedBy = odf
                    .getOWLObjectProperty( IRI.create( "http://asu.edu/sharpc2b/skos-ext#conceptDenotedBy" ) );
            OWLObjectProperty opCode = odf
                    .getOWLObjectProperty(  IriUtil.opsIRI( "opCode" ) );

            OWLObjectIntersectionOf operatorCodeRestr = odf.getOWLObjectIntersectionOf( odf.getOWLClass( IriUtil.opsIRI( "OperatorConceptCode" ) ),
                                                                                        odf.getOWLDataHasValue( skosNotation,
                                                                                                                odf.getOWLLiteral( opName ) ) );

            requirements.add( odf.getOWLObjectSomeValuesFrom( opCode, operatorCodeRestr ) );

            closures.add( odf.getOWLObjectAllValuesFrom( opCode, operatorCodeRestr ) );

                /* AND if n-Ary, all operands are of a particular type, and at least one. */

            boolean nary = arity == ARITY_NARY;
            boolean listArg = arity == ARITY_LIST;
            boolean hasArity = arity >= ARITY_NULLARY;

            if (nary)
            {
                requirements.add( odf.getOWLObjectAllValuesFrom( hasOperand,
                                                                 getExpressionTypeClass( op1TypeName ) ) );
                requirements.add( odf.getOWLObjectSomeValuesFrom( hasOperand,
                                                                  getExpressionTypeClass( op1TypeName ) ) );
            }

                /* AND if aggregate type, only a single operand of type = ListExpression */
            if (listArg)
            {
                OWLClass listExpr = getExpressionTypeClass( "List" );
                requirements.add( odf.getOWLObjectExactCardinality( 1, hasOperand ) );
                requirements.add( odf.getOWLObjectSomeValuesFrom( hasOperand, listExpr ) );
                closures.add( odf.getOWLObjectAllValuesFrom( hasOperand,
                                                             listExpr ) );

            } else {
                /* AND if Arity type, add requirement for first operand */
                if (hasArity)
                {
                    requirements.add( odf.getOWLObjectSomeValuesFrom( hasOperand1,
                                                                      getExpressionTypeClass( op1TypeName ) ) );
                    closures.add( odf.getOWLObjectAllValuesFrom( hasOperand1,
                                                                 getExpressionTypeClass( op1TypeName ) ) );
                }
                /* AND if Binary or Ternary, add requirement for second operand */
                if (2 <= arity)
                {
                    requirements.add( odf.getOWLObjectSomeValuesFrom( hasOperand2,
                                                                      getExpressionTypeClass( op2TypeName ) ) );
                    closures.add( odf.getOWLObjectAllValuesFrom( hasOperand2,
                                                                 getExpressionTypeClass( op2TypeName ) ) );

                }
                /* AND if Ternary, add requirement for third operand */
                if (3 <= arity)
                {
                    requirements.add( odf.getOWLObjectSomeValuesFrom( hasOperand3,
                                                                      getExpressionTypeClass( op3TypeName ) ) );
                    closures.add( odf.getOWLObjectAllValuesFrom( hasOperand3,
                                                                 getExpressionTypeClass( op3TypeName ) ) );

                }
            }

            switch ( arity ) {
                case 0:
                    closures.add( odf.getOWLClass( IriUtil.opsIRI( "NullaryExpression" ) ) );
                    break;
                case 1:
                    closures.add( odf.getOWLClass( IriUtil.opsIRI( "UnaryExpression" ) ) );
                    break;
                case 2:
                    closures.add( odf.getOWLClass( IriUtil.opsIRI( "BinaryExpression" ) ) );
                    break;
                case 3:
                    closures.add( odf.getOWLClass( IriUtil.opsIRI( "TernaryExpression" ) ) );
                    break;
                default:
                    closures.add( odf.getOWLClass( IriUtil.opsIRI( "NAryExpression" ) ) );
                    break;
            }

            OWLObjectIntersectionOf andExpression = odf.getOWLObjectIntersectionOf( requirements );

            addAxiom( odf.getOWLEquivalentClassesAxiom( exprClass, andExpression ) );

            for ( OWLClassExpression closure : closures ) {
                addAxiom( odf.getOWLSubClassOfAxiom( exprClass, closure ) );
            }
//            addAxiom( odf.getOWLDeclarationAxiom( exprClass ) );
        }
    }

    private void assertExpressionAxioms ()
    {

    }

    private void addMissingCommonAxioms ()
    {

    }

    private void assertOperandTypes (final int arity,
                                     final OWLNamedIndividual operator,
                                     final OWLNamedIndividual operand1Type,
                                     final OWLNamedIndividual operand2Type,
                                     final OWLNamedIndividual operand3Type)
    {
        OWLObjectProperty operandTypeRelationship = odf
                .getOWLObjectProperty(  IriUtil.opsIRI( "hasOperandType" ) );
        OWLObjectProperty firstOperandTypeRelationship = odf
                .getOWLObjectProperty(  IriUtil.opsIRI( "hasFirstOperandType" ) );
        OWLObjectProperty secondOperandTypeRelationship = odf
                .getOWLObjectProperty(  IriUtil.opsIRI( "hasSecondOperandType" ) );
        OWLObjectProperty thirdOperandTypeRelationship = odf
                .getOWLObjectProperty(  IriUtil.opsIRI( "hasThirdOperandType" ) );

            /* first operand type */
        OWLObjectProperty opRelationship = 0 < arity
                ? firstOperandTypeRelationship
                : operandTypeRelationship;
        addAxiom( odf.getOWLObjectPropertyAssertionAxiom( opRelationship, operator, operand1Type ) );

            /* second operand type */
        if (2 <= arity)
        {
            addAxiom( odf.getOWLObjectPropertyAssertionAxiom( secondOperandTypeRelationship, operator,
                                                              operand2Type ) );
        }

            /* third operand type */
        if (3 <= arity)
        {
            addAxiom( odf.getOWLObjectPropertyAssertionAxiom( thirdOperandTypeRelationship, operator,
                                                              operand3Type ) );
        }
    }

    private void addOperatorType (final OWLNamedIndividual operator,
                                  int arity)
    {
        OWLClass unaryOperator = odf.getOWLClass(  IriUtil.opsIRI( "UnaryOperatorConcept" ) );
        OWLClass binaryOperator = odf.getOWLClass(  IriUtil.opsIRI( "BinaryOperatorConcept" ) );
        OWLClass ternaryOperator = odf.getOWLClass(  IriUtil.opsIRI( "TernaryOperatorConcept" ) );
        OWLClass naryOperator = odf.getOWLClass(  IriUtil.opsIRI( "NAryOperatorConcept" ) );
        OWLClass aggregateOperator = odf.getOWLClass(  IriUtil.opsIRI( "AggregateOperatorConcept" ) );

        if (arity == -1)
        {
            addAxiom( odf.getOWLClassAssertionAxiom( naryOperator, operator ) );
        }
        else if (arity == 0)
        {
            addAxiom( odf.getOWLClassAssertionAxiom( aggregateOperator, operator ) );
        }
        else if (arity == 1)
        {
            addAxiom( odf.getOWLClassAssertionAxiom( unaryOperator, operator ) );
        }
        else if (arity == 2)
        {
            addAxiom( odf.getOWLClassAssertionAxiom( binaryOperator, operator ) );
        }
        else if (arity == 3)
        {
            addAxiom( odf.getOWLClassAssertionAxiom( ternaryOperator, operator ) );
        }
    }

    //===============================================================================================

    IRI outputIRI (final String name)
    {
        return IRI.create( outputOntBaseIRI + name );
    }

    void addAxiom (final OWLAxiom axiom)
    {
        oom.addAxiom( ont, axiom );
//        newAxioms.add( axiom );
    }

    void addImports ()
    {
        OWLImportsDeclaration importOps = odf.getOWLImportsDeclaration( IriUtil.sharpEditorIRI( "ops" ) );

        System.out.println( "add owl:imports: " + importOps );

        oom.applyChange( new AddImport( ont, importOps ) );
    }

    private void setupOntologyManager (final OWLOntology ont)
    {
        oom = ont.getOWLOntologyManager();
        odf = oom.getOWLDataFactory();
//        {
//            OWLOntologyFormat ontFormat = oom.getOntologyFormat( ont );
//            if (ontFormat instanceof PrefixOWLOntologyFormat)
//            {
//                this.oFormat = (PrefixOWLOntologyFormat) ontFormat;
//            }
//            else
//            {
//                throw new RuntimeException(
//                        "Current version of this method only works with Ontologies whose " +
//                                "OWLOntologyFormat is a PrefixOWLOntologyFormat.  The Ontology Format" +
//                                "of this Ontology is: " + ontFormat.getClass().getName() );
//            }
//        }
    }

    private Sheet getExcelOperatorsSheet (File excelFile)
    {
        FileInputStream fis = null;
        Workbook workbook;
        try
        {
            System.out.println( "Opening workbook [" + excelFile.getName() + "]" );

            fis = new FileInputStream( excelFile );

            // Open the workbook and then create the FormulaEvaluator and
            // DataFormatter instances that will be needed to, respectively,
            // force evaluation of forumlae found in cells and create a
            // formatted String encapsulating the cells contents.
            workbook = WorkbookFactory.create( fis );
//            this.evaluator = this.workbook.getCreationHelper().createFormulaEvaluator();
//            this.formatter = new DataFormatter(true);
        }
        catch (InvalidFormatException e)
        {
            e.printStackTrace();
            String msg = "Unable to find, load, or read Excel file or find correct Sheet." +
                    " Base Exception message = " + e.getMessage();
            throw new RuntimeException( msg, e );
        }
        catch (IOException e)
        {
            e.printStackTrace();
            String msg = "Unable to find, load, or read Excel file or find correct Sheet." +
                    " Base Exception message = " + e.getMessage();
            throw new RuntimeException( msg, e );
        }
        finally
        {
            if (fis != null)
            {
                try
                {
                    fis.close();
                }
                catch (IOException e)
                {
                    //
                }
            }
        }

        Sheet sheet = workbook.getSheet( "Operators" );
        return sheet;
    }

    /**
     * The OWL Individual for the type, such as realType, stringType, ListType, etc.
     *
     * @param typeNameInSpreadsheet the name in the spreadsheet, e.g., "Real", "T", "Object<T>"
     */
    OWLNamedIndividual getOpsTypeIndividual (final String typeNameInSpreadsheet)
    {
        IRI typeIRI = typeNameIriMap.get( typeNameInSpreadsheet );
        OWLNamedIndividual typeInd = typeIRI == null ? null : odf.getOWLNamedIndividual( typeIRI );
        return typeInd;
    }

    /**
     * The OWL Class for the Expression value type, such as RealExpression, StringExpression,
     * ListExpression, etc.
     *
     * @param typeNameInSpreadsheet the name in the spreadsheet, e.g., "Real", "T", "Object<T>"
     */
    OWLClass getExpressionTypeClass (final String typeNameInSpreadsheet)
    {

        IRI typeIRI = typeExpressionNameMap.get( typeNameInSpreadsheet );
        OWLClass type = typeIRI == null
                ? null
                : odf.getOWLClass( IRI.create( typeIRI.toString() + "Expression" ) );
        return type;
    }

    static void initTypeNameIriMap ()
    {
        typeNameIriMap.put( "Any", IRI.create( typesBaseIRI + "anyType" ) );
        typeNameIriMap.put( "T", IRI.create( typesBaseIRI + "anyType" ) );
        typeNameIriMap.put( "C", IRI.create( typesBaseIRI + "anyType" ) );
        typeNameIriMap.put( "Object", IRI.create( typesBaseIRI + "objectType" ) );
        typeNameIriMap.put( "Object<T>", IRI.create( typesBaseIRI + "objectType" ) );
        typeNameIriMap.put( "Number", IRI.create( typesBaseIRI + "numberType" ) );
        typeNameIriMap.put( "Real", IRI.create( typesBaseIRI + "realType" ) );
        typeNameIriMap.put( "Boolean", IRI.create( typesBaseIRI + "booleanType" ) );
        typeNameIriMap.put( "String", IRI.create( typesBaseIRI + "stringType" ) );
        typeNameIriMap.put( "Time/Duration", IRI.create( typesBaseIRI + "timeDurationType" ) );
        typeNameIriMap.put( "Timestamp", IRI.create( typesBaseIRI + "timestampType" ) );
        typeNameIriMap.put( "DateGranularity", IRI.create( typesBaseIRI + "dateGranularityType" ) );
        typeNameIriMap.put( "Integer", IRI.create( typesBaseIRI + "intType" ) );
        typeNameIriMap.put( "Interval<T>", IRI.create( typesBaseIRI + "intervalType" ) );
        typeNameIriMap.put( "Collection<T>", IRI.create( typesBaseIRI + "collectionType" ) );
        typeNameIriMap.put( "List", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "List<T>", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "List<S>", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "List<String>", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "List<Boolean>", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "Ordered", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "Ordered Type", IRI.create( typesBaseIRI + "listType" ) );
        typeNameIriMap.put( "Scalar", IRI.create( typesBaseIRI + "scalarType" ) );
        typeNameIriMap.put( "Expression<T:S>", IRI.create( typesBaseIRI + "anyType" ) );
    }

    static void initTypeExpressionNameMap ()
    {
        typeExpressionNameMap.put( "Any", IRI.create( typesBaseIRI + "Operator" ) );
        typeExpressionNameMap.put( "T", IRI.create( typesBaseIRI + "Operator" ) );
        typeExpressionNameMap.put( "C", IRI.create( typesBaseIRI + "Operator" ) );
        typeExpressionNameMap.put( "Object", IRI.create( typesBaseIRI + "Object" ) );
        typeExpressionNameMap.put( "Object<T>", IRI.create( typesBaseIRI + "Object" ) );
        typeExpressionNameMap.put( "Number", IRI.create( typesBaseIRI + "Number" ) );
        typeExpressionNameMap.put( "Real", IRI.create( typesBaseIRI + "Real" ) );
        typeExpressionNameMap.put( "Boolean", IRI.create( typesBaseIRI + "Boolean" ) );
        typeExpressionNameMap.put( "String", IRI.create( typesBaseIRI + "String" ) );
        typeExpressionNameMap.put( "Time/Duration", IRI.create( typesBaseIRI + "TimeDuration" ) );
        typeExpressionNameMap.put( "Timestamp", IRI.create( typesBaseIRI + "Timestamp" ) );
        typeExpressionNameMap.put( "DateGranularity", IRI.create( typesBaseIRI + "DateGranularity" ) );
        typeExpressionNameMap.put( "Integer", IRI.create( typesBaseIRI + "Integer" ) );
        typeExpressionNameMap.put( "Interval<T>", IRI.create( typesBaseIRI + "Interval" ) );
        typeExpressionNameMap.put( "Collection<T>", IRI.create( typesBaseIRI + "Collection" ) );
        typeExpressionNameMap.put( "List", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "List<T>", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "List<S>", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "List<String>", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "List<Boolean>", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "Ordered", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "Ordered Type", IRI.create( typesBaseIRI + "List" ) );
        typeExpressionNameMap.put( "Scalar", IRI.create( typesBaseIRI + "Scalar" ) );
        typeExpressionNameMap.put( "Expression<T:S>", IRI.create( typesBaseIRI + "Operator" ) );
    }

    static void initTypeNameMap ()
    {
        typeNameMap.put( "Any", "Any" );
        typeNameMap.put( "T", "Any" );
        typeNameMap.put( "C", "Any" );
        typeNameMap.put( "Object", "Object" );
        typeNameMap.put( "Object<T>", "Object" );
        typeNameMap.put( "Number", "Number" );
        typeNameMap.put( "Real", "Real" );
        typeNameMap.put( "Boolean", "Boolean" );
        typeNameMap.put( "String", "String" );
        typeNameMap.put( "Time/Duration", "TimeDuration" );
        typeNameMap.put( "Timestamp", "Timestamp" );
        typeNameMap.put( "DateGranularity", "DateGranularity" );
        typeNameMap.put( "Integer", "Integer" );
        typeNameMap.put( "Interval<T>", "Interval" );
        typeNameMap.put( "Collection<T>", "Collection" );
        typeNameMap.put( "List", "List" );
        typeNameMap.put( "List<T>", "List" );
        typeNameMap.put( "List<S>", "List" );
        typeNameMap.put( "List<String>", "List" );
        typeNameMap.put( "List<Boolean>", "List" );
        typeNameMap.put( "Ordered", "List" );
        typeNameMap.put( "Ordered Type", "List" );
        typeNameMap.put( "Scalar", "Scalar" );
        typeNameMap.put( "Expression<T:S>", "Any" );
    }



}
