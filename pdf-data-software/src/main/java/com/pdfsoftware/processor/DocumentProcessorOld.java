package com.pdfsoftware.processor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.DocumentLocation;
import com.amazonaws.services.textract.model.DocumentMetadata;
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.GetDocumentTextDetectionRequest;
import com.amazonaws.services.textract.model.GetDocumentTextDetectionResult;
import com.amazonaws.services.textract.model.Relationship;
import com.amazonaws.services.textract.model.S3Object;
import com.amazonaws.services.textract.model.StartDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import com.amazonaws.services.textract.model.StartDocumentTextDetectionRequest;
import com.amazonaws.services.textract.model.StartDocumentTextDetectionResult;
import com.fasterxml.jackson.databind.ObjectMapper;;
public class DocumentProcessorOld {

    private static String startJobId = null;
    private static String strBucket = "pdfsoftware-bkt";
    private static String strBucketKey = "objects"; 
    private static String strDocument = "E://Vinod_Project/github/tabula-pdf-py/Sample bank statement/bank statement_image.pdf"; 
    private static AmazonTextract textract = null;
    private static AmazonS3 s3 = null;  
    
    public enum ProcessType {
        DETECTION,ANALYSIS
    }

    public static void main(String[] args) throws Exception {
        
    	PutObjectResult result = copyFileToS3(strBucket, strBucketKey, strDocument);
    	if(Objects.nonNull(result))
    		processToTextract(strBucket, strBucketKey, ProcessType.ANALYSIS);
    	else
    		throw new Exception("Failed to put object on S3...");
        
        deleteFileFromS3(strBucket, strBucketKey);
    }

	private static void processToTextract(String strBucket, String strDocument, ProcessType eType) throws Exception {
    	System.out.println("In :: DocumentProcessor class with :: processToTextract ");
    	textract = AmazonTextractClientBuilder.defaultClient();
    	System.out.println("Client creation done...");
    	
        switch(eType)
        {
            case DETECTION:
                startDocDetection(strBucket, strDocument);
                if(isNullOrEmpty(startJobId)) {
                	getDocDetectionResults(startJobId);
                	break;
                }else{
                	System.out.println("analysis failed : JobId is null");
                	throw new Exception("JobId Exception");
                }
            case ANALYSIS:
                startDocAnalysis(strBucket, strDocument);
                if(isNullOrEmpty(startJobId)) {
                	GetDocumentAnalysisResult results = getDocAnalysisResults(startJobId);
                	ObjectMapper jsonMapper = new ObjectMapper();
                	String jsonDoc = jsonMapper.writeValueAsString(results);
                	System.out.println("JsonString: " +jsonDoc);
                	break;
                }else{
                	System.out.println("analysis failed : JobId is null");
                	throw new Exception("JobId Exception");
                }

            default:
                System.out.println("Invalid processing type. Choose Detection or Analysis");
                throw new Exception("Invalid processing type");
           
        }
        System.out.println("Finished processing document");
    	
	}

	private static PutObjectResult copyFileToS3(String strBucket, String key ,String strDocument) throws FileNotFoundException, Exception {
		System.out.println("In :: DocumentProcessor class with :: copyFileToS3");
		PutObjectResult result = null;
		String contentType = null;
		
		s3 = AmazonS3ClientBuilder.defaultClient();
		System.out.println("Client creation done...");
		
		try(FileInputStream iFile = new FileInputStream(strDocument)){
			String docName = strDocument.substring(strDocument.lastIndexOf("/")+1, strDocument.length());
			
			if(docName.toUpperCase().endsWith("PDF"))
				contentType = "application/pdf";
			else if(docName.toUpperCase().endsWith("JPEG"))
				contentType = "image/jpeg";
			else if(docName.toUpperCase().endsWith("PNG"))
				contentType = "image/png";
			else
				contentType = "text/plain";
			
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType(contentType);
			strBucketKey = key+"/"+docName;
			result = s3.putObject(strBucket, strBucketKey, iFile, metadata);
			System.out.println("Out :: DocumentProcessor class with :: copyFileToS3");

		}catch (Exception e) {
			System.out.println("Exception occured while copying object: "+strDocument);
		}
		return result;
	}

    private static void deleteFileFromS3(String strBucket, String strKey) throws Exception{
    	System.out.println("In :: DocumentProcessor class with :: deleteFileFromS3");
    	try {
    		s3.deleteObject(strBucket, strKey);
    	}catch (AmazonServiceException ae) {
			System.out.println("Error occured while deleting object: " + strKey);
			throw new Exception(ae);
		}
    	System.out.println("Out :: DocumentProcessor class with :: deleteFileFromS3");
	}

    
    private static void startDocDetection(String strBucket, String strDocument) throws Exception{

        StartDocumentTextDetectionRequest req = new StartDocumentTextDetectionRequest()
                .withDocumentLocation(new DocumentLocation()
                    .withS3Object(new S3Object()
                        .withBucket(strBucket)
                        .withName(strDocument)))
                .withJobTag("DetectingText");

        StartDocumentTextDetectionResult startDocumentTextDetectionResult = textract.startDocumentTextDetection(req);
        startJobId=startDocumentTextDetectionResult.getJobId();
    }
    
  //Gets the results of processing started by StartDocumentTextDetection
    private static void getDocDetectionResults(String startJobId) throws Exception{
        int maxResults=1000;
        String paginationToken=null;
        GetDocumentTextDetectionResult response=null;
        Boolean finished=false;
        
        while (finished==false)
        {
            GetDocumentTextDetectionRequest documentTextDetectionRequest= new GetDocumentTextDetectionRequest()
                    .withJobId(startJobId)
                    .withMaxResults(maxResults)
                    .withNextToken(paginationToken);
            response = textract.getDocumentTextDetection(documentTextDetectionRequest);
            DocumentMetadata documentMetaData=response.getDocumentMetadata();

            System.out.println("Pages: " + documentMetaData.getPages().toString());
            
            //Show blocks information
            List<Block> blocks= response.getBlocks();
            for (Block block : blocks) {
                DisplayBlockInfo(block);
            }
            paginationToken=response.getNextToken();
            if (paginationToken==null)
                finished=true;
            
        }
        
    }

    private static void startDocAnalysis(String strBucket, String strDocument) throws Exception{

        StartDocumentAnalysisRequest docReq = new StartDocumentAnalysisRequest()
                .withFeatureTypes("TABLES","FORMS")
                .withDocumentLocation(new DocumentLocation()
                    .withS3Object(new S3Object()
                        .withBucket(strBucket)
                        .withName(strDocument)))
                .withJobTag("AnalyzingText");
 
        StartDocumentAnalysisResult startDocumentAnalysisResult = textract.startDocumentAnalysis(docReq);
        startJobId = startDocumentAnalysisResult.getJobId();
    }
    //Gets the results of processing started by StartDocumentAnalysis
    private static GetDocumentAnalysisResult getDocAnalysisResults(String startJobId) throws Exception{

        int maxResults=1000;
        String paginationToken = null;
        GetDocumentAnalysisResult docResponse = null;
        GetDocumentAnalysisResult fDocResponse = null;
        Boolean finished = false;
        
        //loops until pagination token is null
        while (finished==false)
        {
            GetDocumentAnalysisRequest documentAnalysisRequest = new GetDocumentAnalysisRequest()
                    .withJobId(startJobId)
                    .withMaxResults(maxResults)
                    .withNextToken(paginationToken);

            docResponse = textract.getDocumentAnalysis(documentAnalysisRequest);
            
            if(Objects.isNull(fDocResponse))
            	fDocResponse = docResponse.clone();
            else
            	fDocResponse.getBlocks().addAll(docResponse.getBlocks());
            
            System.out.println("Blocks: " +fDocResponse.getBlocks().size());
            DocumentMetadata documentMetaData = fDocResponse.getDocumentMetadata();
            System.out.println("Pages: " + documentMetaData.getPages().toString());
            
           

            //Show blocks, confidence and detection times
			/*
			 * List<Block> blocks= docResponse.getBlocks(); for (Block block : blocks) {
			 * DisplayBlockInfo(block); }
			 */
            
            paginationToken=docResponse.getNextToken();
            if (paginationToken == null)
                finished = true;
        }
        
        fDocResponse.setDocumentMetadata(docResponse.getDocumentMetadata());
        fDocResponse.setJobStatus(docResponse.getJobStatus());
        fDocResponse.setNextToken(docResponse.getNextToken());
        fDocResponse.setSdkHttpMetadata(null);
        fDocResponse.setSdkResponseMetadata(null);
        
        return fDocResponse;
    }
    //Displays Block information for text detection and text analysis
    private static void DisplayBlockInfo(Block block) {
        System.out.println("Block Id : " + block.getId());
        if (block.getText()!=null)
            System.out.println("\tDetected text: " + block.getText());
        System.out.println("\tType: " + block.getBlockType());
        
        if (block.getBlockType().equals("PAGE") !=true) {
            System.out.println("\tConfidence: " + block.getConfidence().toString());
        }
        if(block.getBlockType().equals("CELL"))
        {
            System.out.println("\tCell information:");
            System.out.println("\t\tColumn: " + block.getColumnIndex());
            System.out.println("\t\tRow: " + block.getRowIndex());
            System.out.println("\t\tColumn span: " + block.getColumnSpan());
            System.out.println("\t\tRow span: " + block.getRowSpan());

        }
        
        System.out.println("\tRelationships");
        List<Relationship> relationships=block.getRelationships();
        if(relationships!=null) {
            for (Relationship relationship : relationships) {
                System.out.println("\t\tType: " + relationship.getType());
                System.out.println("\t\tIDs: " + relationship.getIds().toString());
            }
        } else {
            System.out.println("\t\tNo related Blocks");
        }

        System.out.println("\tGeometry");
        System.out.println("\t\tBounding Box: " + block.getGeometry().getBoundingBox().toString());
        System.out.println("\t\tPolygon: " + block.getGeometry().getPolygon().toString());
        
        List<String> entityTypes = block.getEntityTypes();
        
        System.out.println("\tEntity Types");
        if(entityTypes!=null) {
            for (String entityType : entityTypes) {
                System.out.println("\t\tEntity Type: " + entityType);
            }
        } else {
            System.out.println("\t\tNo entity type");
        }
        
        if(block.getBlockType().equals("SELECTION_ELEMENT")) {
            System.out.print("    Selection element detected: ");
            if (block.getSelectionStatus().equals("SELECTED")){
                System.out.println("Selected");
            }else {
                System.out.println(" Not selected");
            }
        }
        if(block.getPage()!=null)
            System.out.println("\tPage: " + block.getPage());            
        System.out.println();
    }
    
    private static boolean isNullOrEmpty(String str) {
    	if(str != null && !str.trim().isEmpty())
    		return true;
    	else
    		return false;
    }
}

