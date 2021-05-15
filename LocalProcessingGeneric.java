package com.aarete.doczy.utilitty.localtest;

import static com.aarte.doczy.textractprocessor.processingengine.RuleProcessEngine.applyProcessingRules;
import static com.aarte.doczy.textractprocessor.processingengine.RuleProcessEngine.applyProcessingRulesOnTableCell;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.containsPattern;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getAllWords;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getKeyValue;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getLastPage;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getPageNumber;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getParentFieldList;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getTable;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getTableColumnSize;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getWholeDocument;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.pageLevelString;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.reArrangeFieldList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.aarte.doczy.textractprocessor.bean.ClientConfig;
import com.aarte.doczy.textractprocessor.bean.ContractDetails;
import com.aarte.doczy.textractprocessor.bean.Field;
import com.aarte.doczy.textractprocessor.bean.KeyValueDetail;
import com.aarte.doczy.textractprocessor.bean.Table;
import com.aarte.doczy.textractprocessor.bean.TableRecordBean;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalProcessingGeneric {
	static final Logger logger = LogManager.getLogger(LocalProcessingGeneric.class);

	public static void main(String[] args) throws IOException {

		final File folder = new File("D:\\developer\\OCR_Project\\my_task\\Iterations_dev\\files\\json"); // path for folder
																								// containing
		// jsons
		List<String> result = new ArrayList<>();
		List<ContractDetails> contractDetailsList = new ArrayList<>();
		ContractDetails contractDetails = null;
		List<String> pageDocument = null;
		search(".*\\.json", folder, result);
		for (String f : result) {
			String fileName = f;

			List<KeyValueDetail> keyValueDetails = null;
			try (FileReader reader = new FileReader(f)) {
				GetDocumentAnalysisResult analysisResult = new ObjectMapper().readValue(reader,
						GetDocumentAnalysisResult.class);
				ClientConfig clientConfig = null;
				final File rulesFolder = new File(
						"D:\\developer\\OCR_Project\\my_task\\Iterations_dev\\files\\process_json"); // path

				List<String> resultFiles = new ArrayList<>();

				search(".*\\.json", rulesFolder, resultFiles);
				for (String ruleFile : resultFiles) {
					keyValueDetails = new ArrayList<>();
					String processingRulesfileName = ruleFile;
					try (FileReader reader1 = new FileReader(processingRulesfileName)) {
						clientConfig = new ObjectMapper().readValue(reader1, ClientConfig.class);
					}
					String temDoc = getWholeDocument(analysisResult);
					pageDocument = pageLevelString(analysisResult); // For Iterations - page Level string

					System.out.println("key value" + getKeyValue(analysisResult));

					List<Field> fieldList = reArrangeFieldList(clientConfig.getClientGroups().get(0).getFields());
					if (Objects.nonNull(clientConfig.getClientGroups().get(0).getIterations())
							&& !clientConfig.getClientGroups().get(0).getIterations().isEmpty()) {
						System.out.println("Iterations needed");
						String pattern = clientConfig.getClientGroups().get(0).getIterations().get(0)
								.getRecordPattern();
						int index = 0;
						String patternTable = clientConfig.getClientGroups().get(0).getIterations().get(0)
								.getSplitPattern();
						//String[] splittedDoc = temDoc.split(patternTable);
						String stopWord = clientConfig.getClientGroups().get(0).getIterations().get(0)
								.getIterationStopWord();
						String startWord = clientConfig.getClientGroups().get(0).getIterations().get(0)
								.getIterationStartWord();
						String pageWiseIterationFlag = clientConfig.getClientGroups().get(0).getIterations().get(0)
								.getpageWiseIteration().toLowerCase();

						if (pageWiseIterationFlag.equals("true")) {
							int pageNo = 1;
							for (String pageDoc : pageDocument) {
								System.out.println("Processing iteration page wise: "+pageNo);
								String[] splittedDoc = pageDoc.split(patternTable);
								for (String splittedPart : splittedDoc) {
									splittedPart = splittedPart.replaceFirst(stopWord, "");
									splittedPart = splittedPart.replaceFirst(startWord, "");
									String removeString = pattern;
									while (containsPattern(pattern, splittedPart, index)) {
										keyValueDetails = new ArrayList<>();
										List<Field> finalFieldList = new ArrayList<>();
										for (Field field : fieldList) {
											List<Field> parentFieldList = getParentFieldList(fieldList, field);

											Field tempField = applyProcessingRules(field, pageDoc, getAllWords(analysisResult),
													getKeyValue(analysisResult), fileName, null, parentFieldList, analysisResult,
													splittedPart, pageNo);
											field.setFieldValue(tempField.getFieldValue());
											finalFieldList.add(SerializationUtils.clone(tempField));
										}

										splittedPart = splittedPart.replaceFirst(removeString, "");

										Collections.sort(finalFieldList);
										contractDetails = new ContractDetails();
										KeyValueDetail keyValueDetail = null;

										for (Field field : finalFieldList) {
											keyValueDetail = new KeyValueDetail();
											keyValueDetail.setKey(field.getFieldDisplayName());
											keyValueDetail.setValue(field.getFieldValue());
											keyValueDetail.setComment(field.getFieldComment());
											keyValueDetail.setConfidence(field.getResultConfidence());
											if (!StringUtils.isNullOrEmpty(field.getDisplayOrder())) {
												keyValueDetail.setDisplayOrder(Integer.parseInt(field.getDisplayOrder()));
											}
											keyValueDetails.add(keyValueDetail);
										}

										Collections.sort(keyValueDetails);
										contractDetails.setKeyValueDetailList(keyValueDetails);
										contractDetailsList.add(contractDetails);
									}
									
								}
								pageNo++;
							}
							
						}
						else {
							String[] splittedDoc = temDoc.split(patternTable);
							System.out.println("size:" + splittedDoc.length);
							for (String splittedPart : splittedDoc) {
								splittedPart = splittedPart.replaceFirst(stopWord, "");
								splittedPart = splittedPart.replaceFirst(startWord, "");
								String removeString = pattern;
								while (containsPattern(pattern, splittedPart, index)) {
									keyValueDetails = new ArrayList<>();
									List<Field> finalFieldList = new ArrayList<>();
									for (Field field : fieldList) {
										List<Field> parentFieldList = getParentFieldList(fieldList, field);

										Field tempField = applyProcessingRules(field, splittedPart, getAllWords(analysisResult),
												getKeyValue(analysisResult), fileName, null, parentFieldList, analysisResult, splittedPart, null);
										field.setFieldValue(tempField.getFieldValue());
										finalFieldList.add(SerializationUtils.clone(tempField));
									}

									splittedPart = splittedPart.replaceFirst(removeString, "");

									Collections.sort(finalFieldList);
									contractDetails = new ContractDetails();
									KeyValueDetail keyValueDetail = null;

									for (Field field : finalFieldList) {
										keyValueDetail = new KeyValueDetail();
										keyValueDetail.setKey(field.getFieldDisplayName());
										keyValueDetail.setValue(field.getFieldValue());
										keyValueDetail.setComment(field.getFieldComment());
										keyValueDetail.setConfidence(field.getResultConfidence());
										if (!StringUtils.isNullOrEmpty(field.getDisplayOrder())) {
											keyValueDetail.setDisplayOrder(Integer.parseInt(field.getDisplayOrder()));
										}
										keyValueDetails.add(keyValueDetail);
									}

									Collections.sort(keyValueDetails);
									contractDetails.setKeyValueDetailList(keyValueDetails);
									contractDetailsList.add(contractDetails);
								}
							}
						}
					} else {
						System.out.println("No need of Iterations");

						List<Field> finalFieldList = new ArrayList<>();
						for (Field field : fieldList) {
							List<Field> parentFieldList = getParentFieldList(fieldList, field);
							Field tempField = applyProcessingRules(field, temDoc, getAllWords(analysisResult),
									getKeyValue(analysisResult), fileName, null, parentFieldList, analysisResult, "", null);

							field.setFieldValue(tempField.getFieldValue());
							finalFieldList.add(SerializationUtils.clone(tempField));
						}
						Collections.sort(finalFieldList);
						contractDetails = new ContractDetails();

						KeyValueDetail keyValueDetail = null;

						for (Field field : finalFieldList) {
							keyValueDetail = new KeyValueDetail();
							keyValueDetail.setKey(field.getFieldDisplayName());
							keyValueDetail.setValue(field.getFieldValue());
							keyValueDetail.setComment(field.getFieldComment());
							keyValueDetail.setConfidence(field.getResultConfidence());
							if (!StringUtils.isNullOrEmpty(field.getDisplayOrder())) {
								keyValueDetail.setDisplayOrder(Integer.parseInt(field.getDisplayOrder()));
							}
							keyValueDetails.add(keyValueDetail);

						}
						Collections.sort(keyValueDetails);
						contractDetails.setKeyValueDetailList(keyValueDetails);

						if (Objects.nonNull(clientConfig.getClientGroups().get(0).getTalbles())
								&& !clientConfig.getClientGroups().get(0).getTalbles().isEmpty()) {
							List<KeyValueDetail> keyValueDetailList = null;
							Map<Integer, List<KeyValueDetail>> tableRecordsMap = null;
							KeyValueDetail keyValueDetailForCell = null;
							Map<String, List<TableRecordBean>> tableMap = getTable(analysisResult);

							for (Table table : clientConfig.getClientGroups().get(0).getTalbles()) {
								tableRecordsMap = new HashMap<>();
								keyValueDetailList = new ArrayList<>();
								int startPage = getPageNumber(analysisResult, table.getTableStartWord());
								//int endPage = getPageNumber(analysisResult, table.getTableEndWord());
								int endPage = 0;
								if (!"LAST_PAGE".equals(table.getTableEndWord()))
									endPage = getPageNumber(analysisResult, table.getTableEndWord());
								else
									endPage = getLastPage(analysisResult);
								
								int noOfColumns = table.getNoOfColumns();
								if (startPage == -1) {
									startPage = 1;
								}
								if (logger.isDebugEnabled()) {
									logger.debug(String.format("Start Page %s. End Page %s Number of columns found %s ",
											startPage, endPage, noOfColumns));
								}
								if (endPage != -1) {
									List<TableRecordBean> tableRecordBeans = null;
									int recordNumber = 1;
									List<Field> tableFieldList = table.getTableFieldList();
									for (Map.Entry<String, List<TableRecordBean>> page : tableMap.entrySet()) {
										tableRecordBeans = page.getValue();
										if ((startPage <= tableRecordBeans.get(0).getPageNo()
												&& tableRecordBeans.get(0).getPageNo() <= endPage)
												&& (noOfColumns == getTableColumnSize(tableRecordBeans, noOfColumns))) {
											Collections.sort(tableRecordBeans);
											for (TableRecordBean tableRecordBean : tableRecordBeans) {
												if (!(startPage == tableRecordBean.getPageNo()
														&& tableRecordBean.getRowIndex() == 1)) {
													if (tableRecordBean.getColumnIndex() == 1) {
														keyValueDetailList = new ArrayList<>();
													}

													for (Field field : tableFieldList) {
														if (field.getTableColumnNumber() == tableRecordBean
																.getColumnIndex()) {
															keyValueDetailForCell = applyProcessingRulesOnTableCell(
																	tableRecordBean.getColumnValue(), field,
																	tableRecordBean.getPageNo());
															keyValueDetailList.add(keyValueDetailForCell);
														}
													}

													if (tableRecordBean.getColumnIndex() == noOfColumns) {
														tableRecordsMap.put(recordNumber, keyValueDetailList);
														recordNumber++;
													}
												}
											}
										}
									}
								}
								if (!tableRecordsMap.isEmpty()) {
									contractDetails.setTableRecordsMap(tableRecordsMap);
									break;
								}
							}
						}
						System.out.println();
						contractDetailsList.add(contractDetails);
						System.out.println(contractDetailsList.size());
						// System.out.println(contractDetailsList.get(0).getKeyValueDetailList().get(1));
					}
				}
			}

		}

		try {
			populateExcelFromTemplate(contractDetailsList);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void search(final String pattern, final File folder, List<String> result) {
		for (final File f : folder.listFiles()) {

			if (f.isDirectory()) {
				search(pattern, f, result);
			}

			if (f.isFile() && f.getName().matches(pattern)) {
				result.add(f.getAbsolutePath());
			}

		}
	}

	public static XSSFWorkbook populateExcelFromTemplate(List<ContractDetails> contractDetailsList) throws IOException {
		XSSFWorkbook xssfWorkbook = null;
		try {
			if (Objects.nonNull(contractDetailsList) && !contractDetailsList.isEmpty()) {
				InputStream ins = new FileInputStream(
						"D:\\developer\\OCR_Project\\my_task\\Iterations_dev\\files\\USC_Data Entry_CSC_Invoices_9-16-2020.xlsx");
				xssfWorkbook = new XSSFWorkbook(ins);
				List<KeyValueDetail> keyValueDetailList = null;
				XSSFSheet sheet = xssfWorkbook.getSheetAt(0);
				int rowNumber = 2;
				Cell cell = null;
				CellStyle style = xssfWorkbook.createCellStyle();
				style.setBorderBottom(CellStyle.BORDER_THIN);
				style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
				style.setBorderRight(CellStyle.BORDER_THIN);
				style.setRightBorderColor(IndexedColors.BLACK.getIndex());
				style.setBorderTop(CellStyle.BORDER_THIN);
				style.setTopBorderColor(IndexedColors.BLACK.getIndex());
				style.setBorderLeft(CellStyle.BORDER_THIN);
				style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
				for (ContractDetails contractDetails : contractDetailsList) {
					if (Objects.isNull(contractDetails.getTableRecordsMap())
							|| contractDetails.getTableRecordsMap().isEmpty()) {
						keyValueDetailList = contractDetails.getKeyValueDetailList();
						Row dataRow = sheet.createRow(rowNumber++);
						int dataColumnNumber = 0;
						for (KeyValueDetail keyValueDetail : keyValueDetailList) {
							cell = dataRow.createCell(dataColumnNumber++);
							cell.setCellValue(keyValueDetail.getValue());
							cell.setCellStyle(style);
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
									cell.setCellStyle(style);

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
		} catch (Exception e) {
			e.printStackTrace();
		}
		FileOutputStream out = new FileOutputStream(
				new File("D:\\developer\\OCR_Project\\my_task\\Iterations_dev\\files\\Output.xlsx"));
		xssfWorkbook.write(out);
		out.close();
		System.out.println("excel created successfully on local disk.");
		return xssfWorkbook;
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
}
