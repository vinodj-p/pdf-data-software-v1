package com.pdfsoftware.processor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.policy.Condition;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Principal;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.Statement.Effect;
import com.amazonaws.auth.policy.actions.SQSActions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.DocumentLocation;
import com.amazonaws.services.textract.model.DocumentMetadata;
import com.amazonaws.services.textract.model.GetDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.GetDocumentTextDetectionRequest;
import com.amazonaws.services.textract.model.GetDocumentTextDetectionResult;
import com.amazonaws.services.textract.model.NotificationChannel;
import com.amazonaws.services.textract.model.Relationship;
import com.amazonaws.services.textract.model.S3Object;
import com.amazonaws.services.textract.model.StartDocumentAnalysisRequest;
import com.amazonaws.services.textract.model.StartDocumentAnalysisResult;
import com.amazonaws.services.textract.model.StartDocumentTextDetectionRequest;
import com.amazonaws.services.textract.model.StartDocumentTextDetectionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfsoftware.processor.model.KeyValueDetails;
import com.pdfsoftware.processor.model.TableData;
import com.pdfsoftware.processor.model.TableDetails;
import com.pdfsoftware.processor.util.PdUtil;

import static com.pdfsoftware.processor.util.PdUtil.getTableData;

public class DocumentProcessor {

    private static String sqsQueueName=null;
    private static String snsTopicName=null;
    private static String snsTopicArn = null;
    private static String roleArn= null;
    private static String sqsQueueUrl = null;
    private static String sqsQueueArn = null;
    private static String startJobId = null;
    private static String bucket = null;
    private static String strBucketKey = null;
    private static String document = null; 
    private static AmazonSQS sqs=null;
    private static AmazonSNS sns=null;
    private static AmazonTextract textract = null;
    private static AmazonS3 s3 = null;
    private static GetDocumentAnalysisResult docAnalysisResults = null;

    public enum ProcessType {
        DETECTION,ANALYSIS
    }

    public static void main(String[] args) throws Exception {
        
        String document = "E://Vinod_Project/github/tabula-pdf-py/Sample bank statement/bank statement_image.pdf";
        String bucket = "pdfsoftware-bkt";
        String roleArn="arn:aws:iam::184903006442:role/TextractRole";
        String bucketKey = "objects";

        sns = AmazonSNSClientBuilder.defaultClient();
        sqs= AmazonSQSClientBuilder.defaultClient();
        textract=AmazonTextractClientBuilder.defaultClient();
        
        createTopicandQueue();
        copyFileToS3(bucket, bucketKey, document);
        
        processDocument(bucket,strBucketKey,roleArn,ProcessType.ANALYSIS);
        deleteTopicandQueue();
        deleteFileFromS3(bucket, strBucketKey);
        System.out.println("Done!");
        
        
    }
    // Creates an SNS topic and SQS queue. The queue is subscribed to the topic. 
   private static void createTopicandQueue()
    {
        //create a new SNS topic
        snsTopicName="AmazonTextractTopic" + Long.toString(System.currentTimeMillis());
        CreateTopicRequest createTopicRequest = new CreateTopicRequest(snsTopicName);
        CreateTopicResult createTopicResult = sns.createTopic(createTopicRequest);
        snsTopicArn=createTopicResult.getTopicArn();
        
        //Create a new SQS Queue
        sqsQueueName="AmazonTextractQueue" + Long.toString(System.currentTimeMillis());
        final CreateQueueRequest createQueueRequest = new CreateQueueRequest(sqsQueueName);
        sqsQueueUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
        sqsQueueArn = sqs.getQueueAttributes(sqsQueueUrl, Arrays.asList("QueueArn")).getAttributes().get("QueueArn");
        
        //Subscribe SQS queue to SNS topic
        String sqsSubscriptionArn = sns.subscribe(snsTopicArn, "sqs", sqsQueueArn).getSubscriptionArn();
        
        // Authorize queue
          Policy policy = new Policy().withStatements(
                  new Statement(Effect.Allow)
                  .withPrincipals(Principal.AllUsers)
                  .withActions(SQSActions.SendMessage)
                  .withResources(new Resource(sqsQueueArn))
                  .withConditions(new Condition().withType("ArnEquals").withConditionKey("aws:SourceArn").withValues(snsTopicArn))
                  );
                  

          Map queueAttributes = new HashMap();
          queueAttributes.put(QueueAttributeName.Policy.toString(), policy.toJson());
          sqs.setQueueAttributes(new SetQueueAttributesRequest(sqsQueueUrl, queueAttributes)); 
          

         System.out.println("Topic arn: " + snsTopicArn);
         System.out.println("Queue arn: " + sqsQueueArn);
         System.out.println("Queue url: " + sqsQueueUrl);
         System.out.println("Queue sub arn: " + sqsSubscriptionArn );
     }
   private static void deleteTopicandQueue()
    {
        if (sqs !=null) {
            sqs.deleteQueue(sqsQueueUrl);
            System.out.println("SQS queue deleted");
        }
        
        if (sns!=null) {
            sns.deleteTopic(new DeleteTopicRequest().withTopicArn(snsTopicArn));
            System.out.println("SNS topic deleted");
        }
    }
    
    //Starts the processing of the input document.
   private static void processDocument(String inBucket, String inDocument, String inRoleArn, ProcessType type) throws Exception
    {
        bucket=inBucket;
        document=inDocument;
        roleArn=inRoleArn;

        switch(type)
        {
            case DETECTION:
                startDocTextDetection(bucket, document);
                System.out.println("Processing type: Detection");
                break;
            case ANALYSIS:
            	startDocAnalysis(bucket,document);
                System.out.println("Processing type: Analysis");
                break;
            default:
                System.out.println("Invalid processing type. Choose Detection or Analysis");
                throw new Exception("Invalid processing type");
           
        }

        System.out.println("Waiting for job: " + startJobId);
        //Poll queue for messages
        List<Message> messages=null;
        int dotLine=0;
        boolean jobFound=false;

        //loop until the job status is published. Ignore other messages in queue.
        do{
            messages = sqs.receiveMessage(sqsQueueUrl).getMessages();
            if (dotLine++<40){
                System.out.print(".");
            }else{
                System.out.println();
                dotLine=0;
            }

            if (!messages.isEmpty()) {
                //Loop through messages received.
                for (Message message: messages) {
                    String notification = message.getBody();

                    // Get status and job id from notification.
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonMessageTree = mapper.readTree(notification);
                    JsonNode messageBodyText = jsonMessageTree.get("Message");
                    ObjectMapper operationResultMapper = new ObjectMapper();
                    JsonNode jsonResultTree = operationResultMapper.readTree(messageBodyText.textValue());
                    JsonNode operationJobId = jsonResultTree.get("JobId");
                    JsonNode operationStatus = jsonResultTree.get("Status");
                    System.out.println("Job found was " + operationJobId);
                    // Found job. Get the results and display.
                    if(operationJobId.asText().equals(startJobId)){
                        jobFound=true;
                        System.out.println("Job id: " + operationJobId );
                        System.out.println("Status : " + operationStatus.toString());
                        if (operationStatus.asText().equals("SUCCEEDED")){
                            switch(type)
                            {
                                case DETECTION:
                                	getDocTextDetectionResults();
                                    break;
                                case ANALYSIS:
                                	docAnalysisResults = getDocAnalysisResults();
                                    break;
                                default:
                                    System.out.println("Invalid processing type. Choose Detection or Analysis");
                                    throw new Exception("Invalid processing type");
                               
                            }
                        }
                        else{
                            System.out.println("Video analysis failed");
                        }

                        sqs.deleteMessage(sqsQueueUrl,message.getReceiptHandle());
                    }

                    else{
                        System.out.println("Job received was not job " +  startJobId);
                        //Delete unknown message. Consider moving message to dead letter queue
                        sqs.deleteMessage(sqsQueueUrl,message.getReceiptHandle());
                    }
                }
            }
            else {
                Thread.sleep(5000);
            }
        } while (!jobFound);
        
    	ObjectMapper jsonMapper = new ObjectMapper();
    	String jsonDoc = jsonMapper.writeValueAsString(docAnalysisResults);
    	System.out.println("JsonString: " +jsonDoc);
    	FileWriter fw = new FileWriter("E:\\Vinod_Project\\github\\pdf-data-software-v1\\output.json");
    	fw.write(jsonDoc);
    	fw.close();
        System.out.println("Finished processing document");
        
		List<KeyValueDetails> keyValueDetailList = null;
		Map<Integer, List<KeyValueDetails>> tableRecordsMap = null;
        Map<String, List<TableData>> tablesData = getTableData(docAnalysisResults);
        
        List<TableData> tableRecords = null;
		int recordNumber = 1;
		tableRecordsMap = new HashMap<>();
		keyValueDetailList = new ArrayList<>();
		
		for (Map.Entry<String, List<TableData>> page : tablesData.entrySet()) {
			String tableKey = page.getKey();
			tableRecords = page.getValue();
			Collections.sort(tableRecords);
			int rowIdxFirst = 1;
			for(TableData table : tableRecords) {
				KeyValueDetails keyVal = new KeyValueDetails();
				int rowIdx = table.getRowIndex();
				int colIdx = table.getColumnIndex();
				String colValue = table.getColumnValue();
				
				keyVal.setKey(colIdx);
				keyVal.setValue(colValue);
				keyValueDetailList.add(keyVal);
				
				if(rowIdxFirst == rowIdx) {
					rowIdxFirst++;
					continue;
				}else {
					tableRecordsMap.put(rowIdx, keyValueDetailList);
				}
			}
			rowIdxFirst = 1;
		}
		
		PdUtil.populateExcelFromTemplate(tableRecordsMap);
		
    }
    
    private static void startDocTextDetection(String bucket, String document) throws Exception{

        //Create notification channel 
        NotificationChannel channel= new NotificationChannel()
                .withSNSTopicArn(snsTopicArn)
                .withRoleArn(roleArn);

        StartDocumentTextDetectionRequest req = new StartDocumentTextDetectionRequest()
                .withDocumentLocation(new DocumentLocation()
                    .withS3Object(new S3Object()
                        .withBucket(bucket)
                        .withName(document)))
                .withJobTag("DetectingText")
                .withNotificationChannel(channel);

        StartDocumentTextDetectionResult startDocumentTextDetectionResult = textract.startDocumentTextDetection(req);
        startJobId=startDocumentTextDetectionResult.getJobId();
    }
    
  //Gets the results of processing started by StartDocumentTextDetection
    private static void getDocTextDetectionResults() throws Exception{
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

    private static void startDocAnalysis(String bucket, String document) throws Exception{
        //Create notification channel  DeleteTopicandQueue();
    	try {
    		NotificationChannel channel= new NotificationChannel()
                    .withSNSTopicArn(snsTopicArn)
                    .withRoleArn(roleArn);
            
            StartDocumentAnalysisRequest req = new StartDocumentAnalysisRequest()
                    .withFeatureTypes("TABLES","FORMS")
                    .withDocumentLocation(new DocumentLocation()
                        .withS3Object(new S3Object()
                            .withBucket(bucket)
                            .withName(document)))
                    .withJobTag("AnalyzingText")
                    .withNotificationChannel(channel);

            StartDocumentAnalysisResult startDocumentAnalysisResult = textract.startDocumentAnalysis(req);
            startJobId=startDocumentAnalysisResult.getJobId();
    	}catch(AmazonServiceException ex) {
    		System.out.println("Excption: "+ex);
    		deleteTopicandQueue();
    	}catch (Exception e) {
    		System.out.println("Excption: "+e);
    		deleteTopicandQueue();
		}

    }
    //Gets the results of processing started by StartDocumentAnalysis
    private static GetDocumentAnalysisResult getDocAnalysisResults() throws Exception{

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
}