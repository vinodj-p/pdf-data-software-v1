package com.aarte.doczy.textractprocessor.util;

import static com.aarte.doczy.textractprocessor.constant.Constants.APPLICATION_EXCEL;
import static com.aarte.doczy.textractprocessor.constant.Constants.DATE_REGEX_MM_DD_YYYY;
import static com.aarte.doczy.textractprocessor.constant.Constants.DAY_MONTH_YEAR_TEXT;
import static com.aarte.doczy.textractprocessor.constant.Constants.DAY_MONTH_YEAR_TEXT2;
import static com.aarte.doczy.textractprocessor.constant.Constants.DAY_MONTH_YEAR_TEXT3;
import static com.aarte.doczy.textractprocessor.constant.Constants.EXCEL_EXTENSION;
import static com.aarte.doczy.textractprocessor.constant.Constants.MINIMUM_WORD_CONFIDENCE;
import static com.aarte.doczy.textractprocessor.constant.Constants.MMM_DD_YYYY;
import static com.aarte.doczy.textractprocessor.constant.Constants.MM_DD_YY;
import static com.aarte.doczy.textractprocessor.constant.Constants.MM_DD_YY_TEXT;
import static com.aarte.doczy.textractprocessor.constant.Constants.MM_DD_YY_2;
import static com.aarte.doczy.textractprocessor.constant.Constants.MM_DD_YY_TEXT2;
import static com.aarte.doczy.textractprocessor.constant.Constants.MONTH_DAY_YEAR_TEXT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.aarete.doczy.exception.DoczyException;
import com.aarte.doczy.textractprocessor.bean.ClientConfig;
import com.aarte.doczy.textractprocessor.bean.ContractDetails;
import com.aarte.doczy.textractprocessor.bean.Field;
import com.aarte.doczy.textractprocessor.bean.KeyValueDetail;
import com.aarte.doczy.textractprocessor.bean.PropertyBean;
import com.aarte.doczy.textractprocessor.bean.TableRecordBean;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.Relationship;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OcrUtil {

	static final Logger logger = LogManager.getLogger(OcrUtil.class);

	private OcrUtil() {
	}

	public static S3ObjectInputStream readFile(AmazonS3 s3Client, String sourceBucket, String filePath) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Fetching file %s.", filePath));
		}
		S3ObjectInputStream s3ObjectInputStream = null;
		if (Objects.nonNull(s3Client) && !StringUtils.isNullOrEmpty(sourceBucket)
				&& !StringUtils.isNullOrEmpty(filePath)) {
			S3Object fullObject = s3Client.getObject(new GetObjectRequest(sourceBucket, filePath));
			if (Objects.nonNull(fullObject)) {
				s3ObjectInputStream = fullObject.getObjectContent();
			}
		}
		return s3ObjectInputStream;
	}

	public static PropertyBean loadPropetyFile(AmazonS3 s3Client) throws Exception {
		PropertyBean propertyBean = new PropertyBean();
		try {
			String propertyFileLocation = System.getenv("PROPERTY_FILE_LOCATION");
			String[] propertyFileLocationSplitPath = propertyFileLocation.split("/", 2);
			String propertyFileBucket = propertyFileLocationSplitPath[0];
			String propertyFilePath = propertyFileLocationSplitPath[1];
			S3ObjectInputStream s3ObjectInputStream = OcrUtil.readFile(s3Client, propertyFileBucket, propertyFilePath);
			if (Objects.nonNull(s3ObjectInputStream)) {
				Properties ocrProperties = new Properties();
				ocrProperties.load(s3ObjectInputStream);
				String logLevel = ocrProperties.getProperty("LOG_LEVEL");
				if (!StringUtils.isNullOrEmpty(logLevel)) {
					if (logLevel.equalsIgnoreCase("DEBUG")) {
						Configurator.setRootLevel(Level.DEBUG);
					} else if (logLevel.equalsIgnoreCase("INFO")) {
						Configurator.setRootLevel(Level.INFO);
					} else if (logLevel.equalsIgnoreCase("TRACE")) {
						Configurator.setRootLevel(Level.TRACE);
					} else if (logLevel.equalsIgnoreCase("ALL")) {
						Configurator.setRootLevel(Level.ALL);
					} else if (logLevel.equalsIgnoreCase("OFF")) {
						Configurator.setRootLevel(Level.OFF);
					}
				} else {
					Configurator.setRootLevel(Level.INFO);
				}
				propertyBean.setSqsQueueUrl(ocrProperties.getProperty("SQS_QUEUE_URL"));
				String outputLocation = ocrProperties.getProperty("OUTPUT_LOCATION");
				propertyBean.setProcessType(ocrProperties.getProperty("PROCESS_TYPE"));
				String[] destSplitPath = outputLocation.split("/", 2);
				propertyBean.setTextractReceiverOutputJsonBucket(destSplitPath[0]);
				propertyBean.setTextractReceiverOutputJsonLocation(destSplitPath[1]);
				String processedLocationWithBucket = ocrProperties.getProperty("PROCESSED_LOCATION");
				String[] splitProcessedPath = processedLocationWithBucket.split("/", 2);
				propertyBean.setTextractReceiverProcessedFileBucket(splitProcessedPath[0]);
				propertyBean.setTextractReceiverProcessedFileLocation(splitProcessedPath[1]);
				String unprocessedLocationWithBucket = ocrProperties.getProperty("UNPROCESSED_LOCATION");
				String[] splitUnprocessedPath = unprocessedLocationWithBucket.split("/", 2);
				propertyBean.setTextractReceiverUnprocessedFileBucket(splitUnprocessedPath[0]);
				propertyBean.setTextractReceiverUnprocessedFileLocation(splitUnprocessedPath[1]);

				propertyBean.setMaxCountForRuleProcessFile(
						Integer.parseInt(ocrProperties.getProperty("MAX_COUNT_RULEPROCESSED_FILES")));
				propertyBean.setMaxCountForExcelProcessFile(
						Integer.parseInt(ocrProperties.getProperty("MAX_COUNT_EXCELPROCESS_FILES")));

				String ruleEngineProcessedFileLocationWithBucket = ocrProperties
						.getProperty("RULE_ENGINE_PROCESSED_FILE_LOCATION");
				String[] splitRuleEngineProcessedPath = ruleEngineProcessedFileLocationWithBucket.split("/", 2);
				propertyBean.setRuleProcessingBucket(splitRuleEngineProcessedPath[0]);
				propertyBean.setRuleEngineProcessedFileLocation(
						ocrProperties.getProperty("RULE_ENGINE_PROCESSED_FILE_LOCATION"));
				String ruleEngineUnprocessedFileLocationWithBucket = ocrProperties
						.getProperty("RULE_ENGINE_UNPROCESSED_FILE_LOCATION");
				String[] splitRuleEngineUnprocessedPath = ruleEngineUnprocessedFileLocationWithBucket.split("/", 2);
				propertyBean.setRuleUnprocessingBucket(splitRuleEngineUnprocessedPath[0]);
				propertyBean.setRuleEngineUnprocessedFileLocation(splitRuleEngineUnprocessedPath[1]);
				String excelProcessedFileLocationWithBucket = ocrProperties
						.getProperty("EXCEL_GENERATOR_PROCESSED_FILE_LOCATION");
				String[] splitExcelProcessedFileLocation = excelProcessedFileLocationWithBucket.split("/", 2);
				propertyBean.setExcelGeneratorProcessedFileBucket(splitExcelProcessedFileLocation[0]);
				propertyBean.setExcelGeneratorProcessedFileLocation(
						ocrProperties.getProperty("EXCEL_GENERATOR_PROCESSED_FILE_LOCATION"));
				String excelUnprocessedFileLocationWithBucket = ocrProperties
						.getProperty("EXCEL_GENERATOR_UNPROCESSED_FILE_LOCATION");
				String[] splitExcelUnprocessedFileLocation = excelUnprocessedFileLocationWithBucket.split("/", 2);
				propertyBean.setExcelGeneratorUnprocessedFileBucket(splitExcelUnprocessedFileLocation[0]);
				propertyBean.setExcelGeneratorUnprocessedFileLocation(
						ocrProperties.getProperty("EXCEL_GENERATOR_UNPROCESSED_FILE_LOCATION"));

				propertyBean.setSnsTopicArn(ocrProperties.getProperty("SNS_TOPIC_ARN"));
				propertyBean.setRoleArn(ocrProperties.getProperty("TEXTRACT_ROLE_ARN"));
				propertyBean.setInputLocation(ocrProperties.getProperty("INPUT_LOCATION"));
				propertyBean.setNumOfFiles(ocrProperties.getProperty("NO_OF_FILES"));
				String inputLocation = ocrProperties.getProperty("INPUT_LOCATION");
				String[] sourceSplitPath = inputLocation.split("/", 2);
				propertyBean.setSourceDocumentPdfBucket(sourceSplitPath[0]);
				propertyBean.setSourceDocumentPdfLocation(sourceSplitPath[1]);
				String stagingLocationWithBucket = ocrProperties.getProperty("STAGING_LOCATION");
				String[] stagingSplitPath = stagingLocationWithBucket.split("/", 2);
				propertyBean.setTextractSenderStagingPdfBucket(stagingSplitPath[0]);
				propertyBean.setStagingLocation(stagingSplitPath[1]);

				propertyBean.setProcessingRuleJsonPath(ocrProperties.getProperty("PROCESSING_RULE_JSON_PATH"));
				propertyBean.setStagingOutputJsonLocation(ocrProperties.getProperty("STAGING_OUTPUT_JSON_LOCATION"));
				propertyBean.setProcessedJsonResonseLocation(ocrProperties.getProperty("PROCESSED_JSON_RESPOSE"));
				propertyBean.setProcessedExcelResponseLocation(ocrProperties.getProperty("PROCESSED_EXCEL_RESPONSE"));
				propertyBean.setMailSenderType(ocrProperties.getProperty("MAIL_SENDER_TYPE"));

				propertyBean
						.setEmailTemplateConfigPath(ocrProperties.getProperty("PROPERTY_EMAIL_TEMPLATE_CONFIG_PATH"));
				propertyBean.setFileAttachmentPath(ocrProperties.getProperty("PROPERTY_FILE_ATTACHMENT_PATH"));
				propertyBean.setSmtpConfigPath(ocrProperties.getProperty("PROPERTY_SMTP_CONFIG_PATH"));
				propertyBean.setBucketName(ocrProperties.getProperty("PROPERTY_BUCKET_NAME"));
				propertyBean.setTemplateExcelFile(ocrProperties.getProperty("TEMPLATE_EXCEL_FILE"));

				if (logger.isDebugEnabled()) {
					logger.debug(String.format("Property Bean loaded = %s.", propertyBean));
				}
			} else {
				throw new DoczyException("Error in loading property file");
			}

		} catch (IOException e) {
			logger.error(String.format("Error in loading property file. %s. ", e));
			throw e;
		}

		return propertyBean;
	}

	public static List<KeyValueDetail> getKeyValue(GetDocumentAnalysisResult analysisResult) {
		java.util.List<Block> blocks = analysisResult.getBlocks();
		Map<String, Block> keyMap = new HashMap<>();
		Map<String, Block> valueMap = new HashMap<>();
		Map<String, Block> blockMap = new HashMap<>();

		for (Block block : blocks) {
			blockMap.put(block.getId(), block);
			if ("KEY_VALUE_SET".equals(block.getBlockType())) {
				if (block.getEntityTypes().contains("KEY")) {
					keyMap.put(block.getId(), block);
				} else {
					valueMap.put(block.getId(), block);
				}
			}
		}
		Block valueBlock = null;
		String key;
		String value;
		KeyValueDetail keyValueDetail = null;
		List<KeyValueDetail> keyValueDetails = new ArrayList<>();
		for (Map.Entry<String, Block> entry : keyMap.entrySet()) {
			valueBlock = findValueBlock(entry.getValue(), valueMap);
			if (null != valueBlock) {
				key = getText(entry.getValue(), blockMap);
				if (!StringUtils.isNullOrEmpty(key)) {
					value = getText(valueBlock, blockMap);
					keyValueDetail = new KeyValueDetail();
					keyValueDetail.setKey(key);
					keyValueDetail.setValue(value);
					keyValueDetail.setConfidence(valueBlock.getConfidence());
					keyValueDetails.add(keyValueDetail);
				}
			}
		}
		return keyValueDetails;
	}

	public static List<Block> getAllWords(GetDocumentAnalysisResult analysisResult) {
		List<Block> wordList = new ArrayList<>();
		List<Block> blocks = analysisResult.getBlocks();
		for (Block block : blocks) {
			if ("WORD".equals(block.getBlockType()) && !StringUtils.isNullOrEmpty(block.getText())) {
				wordList.add(block);
			}
		}
		return wordList;
	}

	public static List<String> getAllLines(GetDocumentAnalysisResult analysisResult) {
		List<String> lineList = new ArrayList<>();
		List<Block> blocks = analysisResult.getBlocks();
		for (Block block : blocks) {
			if ("LINE".equals(block.getBlockType()) && !StringUtils.isNullOrEmpty(block.getText())) {
				lineList.add(block.getText());
			}
		}
		return lineList;
	}

	private static Block findValueBlock(Block keyBlock, Map<String, Block> valueMap) {
		if (null != keyBlock && null != keyBlock.getRelationships()) {
			for (Relationship relationship : keyBlock.getRelationships()) {
				if (null != relationship && "VALUE".equals(relationship.getType())) {
					for (String id : relationship.getIds()) {
						return valueMap.get(id);
					}
				}

			}
		}
		return null;
	}

	private static String getText(Block block, Map<String, Block> blockMap) {
		StringBuilder text = new StringBuilder();
		Block word;
		if (null != block && null != block.getRelationships()) {
			for (Relationship relationship : block.getRelationships()) {
				if ("CHILD".equals(relationship.getType())) {
					for (String childId : relationship.getIds()) {
						word = blockMap.get(childId);
						if (null != word && "WORD".equals(word.getBlockType())
								&& !StringUtils.isNullOrEmpty(word.getText())) {
							text.append(word.getText());
						}
					}
				}
			}
		}
		return text.toString();
	}

	public static String getWholeDocument(GetDocumentAnalysisResult analysisResult) {
		java.util.List<Block> blocks = analysisResult.getBlocks();
		StringBuilder document = new StringBuilder();
		Map<Integer, Block> pageMap = new HashMap<>();
		Map<String, String> lineMap = new HashMap<>();
		for (Block block : blocks) {
			if (block.getBlockType().equals("LINE") && Objects.nonNull(block.getText())) {
				lineMap.put(block.getId(), block.getText());
			} else if (block.getBlockType().equals("PAGE")) {
				pageMap.put(block.getPage(), block);
			}
		}
		int pageNumber = pageMap.size();

		Block pageBlock = null;
		String line = null;
		for (int index = 1; index <= pageNumber; index++) {
			pageBlock = pageMap.get(index);
			if (null != pageBlock && null != pageBlock.getRelationships()) {
				for (Relationship relationship : pageBlock.getRelationships()) {
					if ("CHILD".equals(relationship.getType())) {
						for (String childId : relationship.getIds()) {
							line = lineMap.get(childId);
							if (!StringUtils.isNullOrEmpty(line)) {
								document.append(line);
								document.append(" ");
							}
						}
					}
				}
			}
		}
		String finalDocument = document.toString();
		return finalDocument.replace("  ", " ");
	}

	public static List<String> getAllFileList(AmazonS3 s3Client, String sourceBucket, String sourceLocation,
			int numberOfFiles, String fileExtension) {
		ArrayList<String> fileList = new ArrayList<>();
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Searching files at %s.", sourceLocation));
		}
		ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(sourceBucket).withPrefix(sourceLocation);
		ListObjectsV2Result listing = s3Client.listObjectsV2(req);
		for (S3ObjectSummary summary : listing.getObjectSummaries()) {
			if (summary.getSize() > 0 && numberOfFiles > fileList.size() && summary.getKey().contains(fileExtension)) {
				fileList.add(summary.getKey());
			}
		}
		return fileList;
	}

	public static void moveFile(AmazonS3 s3Client, String sourceFileLocation, String destinationFileLocation) {
		String[] sourceSplitPath = sourceFileLocation.split("/", 2);
		String[] destSplitPath = destinationFileLocation.split("/", 2);
		try {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Moving file %s to %s.", sourceSplitPath[1], destSplitPath[1]));
			}
			// Copy the object into a new object in the same bucket.
			CopyObjectRequest copyObjRequest = new CopyObjectRequest(sourceSplitPath[0], sourceSplitPath[1],
					destSplitPath[0], destSplitPath[1]);
			s3Client.copyObject(copyObjRequest);
			DeleteObjectRequest request = new DeleteObjectRequest(sourceSplitPath[0], sourceSplitPath[1]);

			// Issue request
			s3Client.deleteObject(request);
		} catch (AmazonServiceException e) {
			logger.error(String.format("Exception while moving processed file %s to %s : %s ", sourceSplitPath[1],
					destSplitPath[1], e));
			throw e;
		} catch (SdkClientException e) {
			logger.error(String.format("Exception moving processed file %s to %s : %s ", sourceSplitPath[1],
					destSplitPath[1], e));
			throw e;
		}
	}

	public static ClientConfig loadProcessingRules(AmazonS3 s3Client, String processingRuleJsonPath)
			throws IOException {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading processing engine");
		}
		String[] processingRuleJsonSplitPath = processingRuleJsonPath.split("/", 2);
		String sourceBucket = processingRuleJsonSplitPath[0];
		String filePath = processingRuleJsonSplitPath[1];
		S3ObjectInputStream s3ObjectInputStream = readFile(s3Client, sourceBucket, filePath);
		return new ObjectMapper().readValue(s3ObjectInputStream, ClientConfig.class);
	}

	public static XSSFWorkbook createExcel(List<ContractDetails> contractDetailsList) throws IOException {
		XSSFWorkbook workbook = null;
		if (Objects.nonNull(contractDetailsList) && !contractDetailsList.isEmpty()) {
			workbook = new XSSFWorkbook();
			XSSFSheet sheet = workbook.createSheet("Contract Details List");

			// Header row creation
			int rowNumber = 0;
			Row headerRow = sheet.createRow(rowNumber++);
			List<KeyValueDetail> keyValueDetailList = contractDetailsList.get(0).getKeyValueDetailList();
			Cell cell = null;
			int headerColumnNumber = 0;
			for (KeyValueDetail keyValueDetail : keyValueDetailList) {
				cell = headerRow.createCell(headerColumnNumber++);
				cell.setCellValue(keyValueDetail.getKey());
			}
			// Data row creation
			for (ContractDetails contractDetails : contractDetailsList) {
				keyValueDetailList = contractDetails.getKeyValueDetailList();
				Row dataRow = sheet.createRow(rowNumber++);
				int dataColumnNumber = 0;
				for (KeyValueDetail keyValueDetail : keyValueDetailList) {
					cell = dataRow.createCell(dataColumnNumber++);
					cell.setCellValue(keyValueDetail.getValue());

					/*
					 * if (!StringUtils.isNullOrEmpty(keyValueDetail.getComment())) {
					 * addCommentsToExcel(cell, keyValueDetail.getComment()); }
					 */
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Excel Created : %s ", workbook));
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("No records to create excel.");
			}
		}
		return workbook;

	}

	public static void loadExcelToS3(AmazonS3 s3Client, XSSFWorkbook workbook, PropertyBean propertyBean)
			throws IOException {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Saving excel to %s", propertyBean.getProcessedExcelResponseLocation()));
		}
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			workbook.write(baos);
			ByteArrayInputStream in = new ByteArrayInputStream(baos.toByteArray());
			Long contentLength = Long.valueOf(baos.toByteArray().length);
			ObjectMetadata objectMetaData = new ObjectMetadata();
			objectMetaData.setContentType(APPLICATION_EXCEL);
			objectMetaData.setContentLength(contentLength);
			String excelOutputPath = propertyBean.getProcessedExcelResponseLocation();
			String excelOutputSplitPath[] = excelOutputPath.split("/", 2);
			String excelOutputLocation = excelOutputSplitPath[1];
			String destBucketName = excelOutputSplitPath[0];
			String filename = "Result " + new Timestamp(new Date().getTime()) + EXCEL_EXTENSION;
			String fileNameWithPath = excelOutputLocation + filename;
			s3Client.putObject(destBucketName, fileNameWithPath, in, objectMetaData);
		} catch (Exception e) {
			logger.error(String.format("Exception moving  file : %s ", e));
			throw e;
		}
	}

	private static void addCommentsToExcel(Cell cell, String commentString) {
		Drawing drawing = cell.getSheet().createDrawingPatriarch();
		CreationHelper factory = cell.getSheet().getWorkbook().getCreationHelper();

		ClientAnchor anchor = factory.createClientAnchor();
		int width = 3;
		int height = 2;
		anchor.setCol1(cell.getColumnIndex());
		anchor.setCol2(cell.getColumnIndex() + width);
		anchor.setRow1(cell.getRowIndex());
		anchor.setRow2(cell.getRowIndex() + height);
		anchor.setDx1(100);
		anchor.setDx2(100);
		anchor.setDy1(100);
		anchor.setDy2(100);
		Comment comment = drawing.createCellComment(anchor);
		RichTextString str = factory.createRichTextString(commentString);
		comment.setString(str);

		cell.setCellComment(comment);
	}

	public static String convertDate(String inputDate, String format, String dateRegex) {
		try {
			if (Objects.nonNull(inputDate)) {
				if (MONTH_DAY_YEAR_TEXT.equalsIgnoreCase(dateRegex)) {
					inputDate = inputDate.replace(", ", ",");
					inputDate = inputDate.replace(" ,", ",");
					Pattern pattern = Pattern.compile(MONTH_DAY_YEAR_TEXT, Pattern.CASE_INSENSITIVE);
					Matcher matcher = pattern.matcher(inputDate);
					while (matcher.find()) {
						SimpleDateFormat dateTextFormat = new SimpleDateFormat(MMM_DD_YYYY);
						Date date = dateTextFormat.parse(inputDate);
						SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
						return dateNumberformat.format(date);
					}
				} else if (DAY_MONTH_YEAR_TEXT.equalsIgnoreCase(dateRegex)) {
					String date;
					String month;
					String year;

					Pattern patternDate = Pattern.compile(DAY_MONTH_YEAR_TEXT, Pattern.CASE_INSENSITIVE);
					Matcher matcherDate = patternDate.matcher(inputDate);
					while (matcherDate.find()) {
						date = matcherDate.group(2);
						month = matcherDate.group(3);
						year = matcherDate.group(15);
						if (!StringUtils.isNullOrEmpty(date) && !StringUtils.isNullOrEmpty(month)
								&& !StringUtils.isNullOrEmpty(year)) {
							String concatenatedDate = month + " " + date + ", " + year;
							SimpleDateFormat dateTextFormat = new SimpleDateFormat(MMM_DD_YYYY);
							Date createdDate = dateTextFormat.parse(concatenatedDate);
							SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
							return dateNumberformat.format(createdDate);
						}
					}
				} else if (DATE_REGEX_MM_DD_YYYY.equalsIgnoreCase(dateRegex)) {

					SimpleDateFormat dateTextFormat = new SimpleDateFormat("MM/dd/yyyy");
					Date createdDate = dateTextFormat.parse(inputDate);
					SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
					return dateNumberformat.format(createdDate);
				} else if (MM_DD_YY_TEXT.equalsIgnoreCase(dateRegex)) {
					inputDate = inputDate.replace(":", ".");
					Pattern pattern = Pattern.compile(MM_DD_YY_TEXT, Pattern.CASE_INSENSITIVE);
					Matcher matcher = pattern.matcher(inputDate);
					while (matcher.find()) {
						SimpleDateFormat dateTextFormat = new SimpleDateFormat(MM_DD_YY);
						Date date = dateTextFormat.parse(inputDate);
						SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
						return dateNumberformat.format(date);
					}
				} else if (MM_DD_YY_TEXT2.equalsIgnoreCase(dateRegex)) {
					Pattern pattern = Pattern.compile(MM_DD_YY_TEXT2, Pattern.CASE_INSENSITIVE);
					Matcher matcher = pattern.matcher(inputDate);
					while (matcher.find()) {
						SimpleDateFormat dateTextFormat = new SimpleDateFormat(MM_DD_YY_2);
						Date date = dateTextFormat.parse(inputDate);
						SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
						return dateNumberformat.format(date);
					}
				} else if (DAY_MONTH_YEAR_TEXT2.equalsIgnoreCase(dateRegex)) {
					String date;
					String month;
					String year;

					Pattern patternDate = Pattern.compile(DAY_MONTH_YEAR_TEXT2, Pattern.CASE_INSENSITIVE);
					Matcher matcherDate = patternDate.matcher(inputDate);
					while (matcherDate.find()) {
						date = matcherDate.group(2);
						month = matcherDate.group(3);
						year = matcherDate.group(15);
						if (!StringUtils.isNullOrEmpty(date) && !StringUtils.isNullOrEmpty(month)
								&& !StringUtils.isNullOrEmpty(year)) {
							String concatenatedDate = month + " " + date + ", " + year;
							SimpleDateFormat dateTextFormat = new SimpleDateFormat(MMM_DD_YYYY);
							Date createdDate = dateTextFormat.parse(concatenatedDate);
							SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
							return dateNumberformat.format(createdDate);
						}
					}
				} else if (DAY_MONTH_YEAR_TEXT3.equalsIgnoreCase(dateRegex)) {

					SimpleDateFormat dateTextFormat = new SimpleDateFormat("dd-MMM-yy");
					Date createdDate = dateTextFormat.parse(inputDate);
					SimpleDateFormat dateNumberformat = new SimpleDateFormat(format);
					return dateNumberformat.format(createdDate);
				}
			}
		} catch (ParseException e) {
			logger.error(String.format("Exception formatting date %s : %s ", inputDate, e));
			logger.error("Exception Details : ", e.fillInStackTrace());
		}
		return inputDate;
	}

	public static String addDate(String inputDate, int monthsToAdd, String format) {
		try {
			if (!StringUtils.isNullOrEmpty(inputDate)) {
				SimpleDateFormat dateFormat = new SimpleDateFormat(format);
				Date date = dateFormat.parse(inputDate);
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				calendar.add(Calendar.MONTH, monthsToAdd);
				calendar.add(Calendar.DATE, -1);
				Date addedDate = calendar.getTime();
				return dateFormat.format(addedDate);
			}
		} catch (ParseException e) {
			logger.error(String.format("Exception formatting date %s : %s ", inputDate, e));
			logger.error("Exception Details : ", e.fillInStackTrace());
		}
		return inputDate;
	}

	public static List<Field> reArrangeFieldList(List<Field> fieldList) {
		List<Field> arrangedFieldList = new ArrayList<>();

		while (arrangedFieldList.size() < fieldList.size()) {
			for (Field field : fieldList) {
				if (StringUtils.isNullOrEmpty(field.getParentFieldId())) {
					Field temp1Field = arrangedFieldList.stream()
							.filter(parentField -> field.getFieldId().equals(parentField.getFieldId())).findAny()
							.orElse(null);
					if (Objects.isNull(temp1Field)) {
						arrangedFieldList.add(field);
					}
				} else {
					Field tempField = arrangedFieldList.stream()
							.filter(parentField -> field.getParentFieldId().equals(parentField.getFieldId())).findAny()
							.orElse(null);
					if (Objects.nonNull(tempField)) {
						Field temp2Field = arrangedFieldList.stream()
								.filter(parentField -> field.getFieldId().equals(parentField.getFieldId())).findAny()
								.orElse(null);
						if (Objects.isNull(temp2Field)) {
							arrangedFieldList.add(field);
						}
					}
				}
			}
		}
		return arrangedFieldList;
	}

	public static List<Field> getParentFieldList(List<Field> allFieldList, Field field) {
		return allFieldList.stream().filter(parentField -> field.getParentFieldId().equals(parentField.getFieldId()))
				.collect(Collectors.toList());
	}

	public static XSSFWorkbook populateExcelFromTemplate(AmazonS3 s3Client, PropertyBean propertyBean,
			List<ContractDetails> contractDetailsList) throws IOException {
		XSSFWorkbook xssfWorkbook = null;
		try {
			if (Objects.nonNull(contractDetailsList) && !contractDetailsList.isEmpty()) {
				String[] splitProcessedPath = propertyBean.getTemplateExcelFile().split("/", 2);
				String bucketName = splitProcessedPath[0];
				String key = splitProcessedPath[1];
				S3Object s3Object = s3Client.getObject(bucketName, key);
				xssfWorkbook = new XSSFWorkbook(s3Object.getObjectContent());
				List<KeyValueDetail> keyValueDetailList = null;
				XSSFSheet sheet = xssfWorkbook.getSheetAt(0);
				int rowNumber = 3;
				Cell cell = null;
				CellStyle style = xssfWorkbook.createCellStyle();
				CellStyle redStyle = xssfWorkbook.createCellStyle();
				style.setBorderBottom(CellStyle.BORDER_THIN);
				style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
				style.setBorderRight(CellStyle.BORDER_THIN);
				style.setRightBorderColor(IndexedColors.BLACK.getIndex());
				style.setBorderTop(CellStyle.BORDER_THIN);
				style.setTopBorderColor(IndexedColors.BLACK.getIndex());
				style.setBorderLeft(CellStyle.BORDER_THIN);
				style.setLeftBorderColor(IndexedColors.BLACK.getIndex());

				redStyle.setBorderBottom(CellStyle.BORDER_THIN);
				redStyle.setBottomBorderColor(IndexedColors.BLACK.getIndex());
				redStyle.setBorderRight(CellStyle.BORDER_THIN);
				redStyle.setRightBorderColor(IndexedColors.BLACK.getIndex());
				redStyle.setBorderTop(CellStyle.BORDER_THIN);
				redStyle.setTopBorderColor(IndexedColors.BLACK.getIndex());
				redStyle.setBorderLeft(CellStyle.BORDER_THIN);
				redStyle.setLeftBorderColor(IndexedColors.BLACK.getIndex());
				redStyle.setFillBackgroundColor(IndexedColors.RED.getIndex());
				redStyle.setFillPattern(CellStyle.LESS_DOTS);

				for (ContractDetails contractDetails : contractDetailsList) {
					if (Objects.isNull(contractDetails.getTableRecordsMap())
							|| contractDetails.getTableRecordsMap().isEmpty()) {
						keyValueDetailList = contractDetails.getKeyValueDetailList();
						Row dataRow = sheet.createRow(rowNumber++);
						int dataColumnNumber = 0;
						for (KeyValueDetail keyValueDetail : keyValueDetailList) {
							cell = dataRow.createCell(dataColumnNumber++);
							cell.setCellValue(keyValueDetail.getValue());

							if (keyValueDetail.getConfidence() < MINIMUM_WORD_CONFIDENCE
									&& keyValueDetail.getConfidence() != 0.0 && !keyValueDetail.getValue().equals("")
									&& keyValueDetail.getValue() != null) {
								cell.setCellStyle(redStyle);
							} else {
								cell.setCellStyle(style);
							}
						}
					} else {
						keyValueDetailList = contractDetails.getKeyValueDetailList();
						Map<Integer, List<KeyValueDetail>> tableRecordsMap = contractDetails.getTableRecordsMap();
						List<KeyValueDetail> keyValueDetailTableRecords = null;
						for (Map.Entry<Integer, List<KeyValueDetail>> tableRecord : tableRecordsMap.entrySet()) {
							int dataColumnNumber = 0;
							keyValueDetailTableRecords = tableRecord.getValue();
							if (isValuePresent(keyValueDetailTableRecords)) {
								keyValueDetailTableRecords.addAll(keyValueDetailList);
								Collections.sort(keyValueDetailTableRecords);
								Row dataRow = sheet.createRow(rowNumber++);
								for (KeyValueDetail keyValueDetailTableRecord : keyValueDetailTableRecords) {
									cell = dataRow.createCell(dataColumnNumber++);
									cell.setCellValue(keyValueDetailTableRecord.getValue());
									if (keyValueDetailTableRecord.getConfidence() < MINIMUM_WORD_CONFIDENCE
											&& keyValueDetailTableRecord.getConfidence() != 0.0
											&& !keyValueDetailTableRecord.getValue().equals("")
											&& keyValueDetailTableRecord.getValue() != null) {
										cell.setCellStyle(redStyle);
									} else {
										cell.setCellStyle(style);
									}
								}
							}
						}
					}
				}
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("No records to create excel.");
				}
			}
		} catch (AmazonServiceException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Exception fetching template file : ", e.fillInStackTrace());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Creating new excel instead of using template.");
			}
			xssfWorkbook = createExcel(contractDetailsList);
		} catch (SdkClientException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Exception fetching template file : ", e.fillInStackTrace());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Creating new excel instead of using template.");
			}
			xssfWorkbook = createExcel(contractDetailsList);
		} catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Exception fetching template file : ", e.fillInStackTrace());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Creating new excel instead of using template.");
			}
			xssfWorkbook = createExcel(contractDetailsList);
		}

		return xssfWorkbook;
	}

	public static String getTablePresence(GetDocumentAnalysisResult analysisResult, String startWord, String endWord) {
		java.util.List<Block> blocks = analysisResult.getBlocks();
		Map<String, Block> pageMap = new HashMap<>();
		Map<String, Block> lineMap = new HashMap<>();
		for (Block block : blocks) {
			if ("PAGE".equals(block.getBlockType())) {
				pageMap.put(block.getId(), block);
			} else if ("LINE".equals(block.getBlockType()) || "TABLE".equals(block.getBlockType())) {
				lineMap.put(block.getId(), block);
			}
		}
		boolean firstLineFound = false;
		boolean tableFound = false;
		boolean secondLineFound = false;
		if (Objects.nonNull(pageMap) && Objects.nonNull(lineMap)) {
			for (Map.Entry<String, Block> page : pageMap.entrySet()) {
				Block pageBlock = page.getValue();
				firstLineFound = false;
				secondLineFound = false;
				if (Objects.nonNull(pageBlock.getRelationships())) {
					for (Relationship relationship : pageBlock.getRelationships()) {
						if (null != relationship && "CHILD".equals(relationship.getType())) {
							for (String id : relationship.getIds()) {
								Block lineBlock = lineMap.get(id);
								if (Objects.nonNull(lineBlock) && "LINE".equalsIgnoreCase(lineBlock.getBlockType())
										&& !StringUtils.isNullOrEmpty(lineBlock.getText())
										&& lineBlock.getText().contains(startWord)) {
									firstLineFound = true;
								}
								if (Objects.nonNull(lineBlock) && "TABLE".equalsIgnoreCase(lineBlock.getBlockType())) {
									tableFound = true;
								}
								if (Objects.nonNull(lineBlock) && "LINE".equalsIgnoreCase(lineBlock.getBlockType())
										&& !StringUtils.isNullOrEmpty(lineBlock.getText())
										&& lineBlock.getText().contains(endWord)) {
									secondLineFound = true;
								}
								if (firstLineFound && tableFound && secondLineFound) {
									return "Y";
								}
							}
						}
					}
				}
			}
		}
		return "N";
	}

	public static int getPageNumber(GetDocumentAnalysisResult analysisResult, String word) {
		List<Block> blocks = analysisResult.getBlocks();
		for (Block block : blocks) {
			if ("LINE".equals(block.getBlockType()) && !StringUtils.isNullOrEmpty(block.getText())
					&& block.getText().toUpperCase().contains(word.toUpperCase())) {
				return block.getPage();
			}
		}
		return -1;
	}

	public static Map<String, List<TableRecordBean>> getTable(GetDocumentAnalysisResult analysisResult) {
		java.util.List<Block> blocks = analysisResult.getBlocks();

		Map<String, Block> tableMap = new HashMap<>();
		Map<String, Block> cellMap = new HashMap<>();
		Map<String, Block> wordMap = new HashMap<>();

		for (Block block : blocks) {
			if ("TABLE".equals(block.getBlockType())) {
				tableMap.put(block.getId(), block);
			} else if ("CELL".equals(block.getBlockType())) {
				cellMap.put(block.getId(), block);
			} else if ("WORD".equals(block.getBlockType())) {
				wordMap.put(block.getId(), block);
			}
		}
		List<TableRecordBean> tableRecordBeanList = new ArrayList<>();
		TableRecordBean tableRecordBean = null;
		Block tableBlock = null;
		Block cellBlock = null;
		Block wordBlock = null;
		for (Map.Entry<String, Block> table : tableMap.entrySet()) {
			tableBlock = table.getValue();
			if (null != tableBlock) {
				for (Relationship relationshipTable : tableBlock.getRelationships()) {
					if (null != relationshipTable && "CHILD".equals(relationshipTable.getType())) {
						for (String cellId : relationshipTable.getIds()) {
							cellBlock = cellMap.get(cellId);
							if (Objects.nonNull(cellBlock)) {
								tableRecordBean = new TableRecordBean();
								tableRecordBean.setColumnIndex(cellBlock.getColumnIndex());
								tableRecordBean.setRowIndex(cellBlock.getRowIndex());
								tableRecordBean.setRowSpan(cellBlock.getRowSpan());
								tableRecordBean.setColumnSpan(cellBlock.getColumnSpan());
								tableRecordBean.setTableId(tableBlock.getId());
								StringBuilder cellValue = new StringBuilder("");
								if (Objects.nonNull(cellBlock.getRelationships())) {
									for (Relationship relationshipCell : cellBlock.getRelationships()) {
										if (null != relationshipCell && "CHILD".equals(relationshipCell.getType())) {
											for (String wordId : relationshipCell.getIds()) {
												wordBlock = wordMap.get(wordId);
												if (Objects.nonNull(wordBlock)) {
													cellValue.append(wordBlock.getText() + " ");
												}
											}
										}
									}
								}
								tableRecordBean.setPageNo(cellBlock.getPage());
								tableRecordBean.setColumnValue(cellValue.toString());
								tableRecordBeanList.add(tableRecordBean);
							}
						}
					}
				}
			}
		}

		Map<String, List<TableRecordBean>> tableCellMap = new HashMap<>();
		List<TableRecordBean> tempList = null;
		Collections.sort(tableRecordBeanList);

		for (TableRecordBean recordList : tableRecordBeanList) {
			tempList = tableCellMap.get(recordList.getTableId());
			if (Objects.isNull(tempList)) {
				tempList = new ArrayList<>();
				tempList.add(recordList);
				tableCellMap.put(recordList.getTableId(), tempList);
			} else {
				tempList.add(recordList);
				tableCellMap.put(recordList.getTableId(), tempList);
			}
		}
		return tableCellMap;
	}

	public static int getTableColumnSize(List<TableRecordBean> tableRecordBeans, int noOfColumns) {
		int maxColumnIndex = 0;
		Collections.sort(tableRecordBeans);
		for (TableRecordBean tableRecordBean : tableRecordBeans) {
			if (maxColumnIndex <= tableRecordBean.getColumnIndex()) {
				maxColumnIndex = tableRecordBean.getColumnIndex();
			} else {
				if (maxColumnIndex != noOfColumns) {
					break;
				} else {
					return maxColumnIndex;
				}
			}
		}
		return maxColumnIndex;
	}

	public static boolean isValuePresent(List<KeyValueDetail> keyValueDetailTableRecords) {
		boolean isBlank = false;
		for (KeyValueDetail keyValueDetail : keyValueDetailTableRecords) {
			if (!StringUtils.isNullOrEmpty(keyValueDetail.getValue())) {
				return true;
			}
		}
		return isBlank;
	}

	public static String getDeviceNameWithNumber(String splittedPart, String regex) {
		String device = "";

		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(splittedPart);
		if (matcher.find()) {
			device = matcher.group(0);
		}
		return device;
	}

	public static boolean containsPattern(String pattern, String text, Integer fromIndex) {
		if (fromIndex != null && fromIndex < text.length())
			return Pattern.compile(pattern).matcher(text).find();

		return Pattern.compile(pattern).matcher(text).find();
	}

	public static int getInvoicePageNumber(GetDocumentAnalysisResult analysisResult, String splittedPart,
			String regex) {
		String word = getDeviceNameWithNumber(splittedPart, regex);
		List<Block> blocks = analysisResult.getBlocks();
		for (Block block : blocks) {
			if ("LINE".equals(block.getBlockType()) && !StringUtils.isNullOrEmpty(block.getText())
					&& block.getText().toUpperCase().contains(word.toUpperCase())) {
				return block.getPage();
			}
		}
		return -1;
	}

	public static int getSplittedInvoicePageNumber(GetDocumentAnalysisResult analysisResult, String splittedPart,
			String regex) {
		String word = getDeviceNameWithNumber(splittedPart, regex);
		List<Block> blocks = analysisResult.getBlocks();
		for (Block block : blocks) {
			if ("LINE".equals(block.getBlockType()) && !StringUtils.isNullOrEmpty(block.getText())
					&& block.getText().toUpperCase().contains(word.toUpperCase())) {
				return (block.getPage() + 1) / 2;
			}
		}
		return -1;
	}

	public static List<ClientConfig> loadMultipalProcessingRules(AmazonS3 s3Client, String processingRuleJsonPath)
			throws IOException {
		if (logger.isDebugEnabled()) {
			logger.debug("Loading processing engine");
		}
		String[] processingRuleJsonSplitPath = processingRuleJsonPath.split("/", 2);
		String sourceBucket = processingRuleJsonSplitPath[0];
		String folderPath = processingRuleJsonSplitPath[1];
		List<String> filenames = getObjectslistFromFolder(s3Client, sourceBucket, folderPath);
		List<ClientConfig> clientConfigList = new ArrayList<ClientConfig>();
		for (String filePath : filenames) {
			S3ObjectInputStream s3ObjectInputStream = readFile(s3Client, sourceBucket, filePath);
			clientConfigList.add(new ObjectMapper().readValue(s3ObjectInputStream, ClientConfig.class));
		}
		return clientConfigList;
	}

	public static List<String> getObjectslistFromFolder(AmazonS3 s3Client, String bucketName, String folderKey) {

		ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucketName)
				.withPrefix(folderKey + "/");

		List<String> keys = new ArrayList<>();
		ObjectListing objects = s3Client.listObjects(listObjectsRequest);
		for (;;) {
			List<S3ObjectSummary> summaries = objects.getObjectSummaries();
			if (summaries.size() < 1) {
				break;
			}
			for (S3ObjectSummary summery : summaries) {
				if (summery.getKey().matches(".*\\.json")) {
					keys.add(summery.getKey());
				}
			}
			objects = s3Client.listNextBatchOfObjects(objects);
		}
		return keys;
	}

	public static List<String> pageLevelString(GetDocumentAnalysisResult analysisResult) {
		java.util.List<Block> blocks = analysisResult.getBlocks();
		Map<Integer, Block> pageMap = new HashMap<>();
		Map<String, String> lineMap = new HashMap<>();
		for (Block block : blocks) {
			if (block.getBlockType().equals("LINE") && Objects.nonNull(block.getText())) {
				lineMap.put(block.getId(), block.getText());
			} else if (block.getBlockType().equals("PAGE")) {
				pageMap.put(block.getPage(), block);
			}
		}
		if(logger.isDebugEnabled())
			logger.debug(String.format("Total pages: %s", pageMap.size()));
		
		Block pageBlock = null;
		String line = null;
		List<String> pagesDocument = new ArrayList<String>();
		for (int index = 1; index <= pageMap.size(); index++) {
			pageBlock = pageMap.get(index);
			StringBuilder document = new StringBuilder();
			if (null != pageBlock && null != pageBlock.getRelationships()) {
				for (Relationship relationship : pageBlock.getRelationships()) {
					if ("CHILD".equals(relationship.getType())) {
						for (String childId : relationship.getIds()) {
							line = lineMap.get(childId);
							if (!StringUtils.isNullOrEmpty(line)) {
								document.append(line);
								document.append(" ");
							}
						}
					}
				}
			}
			pagesDocument.add(document.toString());
		}
		return pagesDocument;
	}
	
	public static int getLastPage(GetDocumentAnalysisResult analysisResult) {
		java.util.List<Block> blocks = analysisResult.getBlocks();
		Map<Integer, Block> pageMap = new HashMap<>();
		Map<String, String> lineMap = new HashMap<>();
		for (Block block : blocks) {
			if (block.getBlockType().equals("LINE") && Objects.nonNull(block.getText())) {
				lineMap.put(block.getId(), block.getText());
			} else if (block.getBlockType().equals("PAGE")) {
				pageMap.put(block.getPage(), block);
			}
		}
		if(logger.isDebugEnabled())
			logger.debug(String.format("Total pages: %s", pageMap.size()));
		return (!pageMap.isEmpty() ? pageMap.size() : -1);
	}
}