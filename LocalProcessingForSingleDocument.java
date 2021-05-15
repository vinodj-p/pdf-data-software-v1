package com.aarete.doczy.utilitty.localtest;

import static com.aarte.doczy.textractprocessor.processingengine.RuleProcessEngine.applyProcessingRules;
import static com.aarte.doczy.textractprocessor.processingengine.RuleProcessEngine.applyProcessingRulesOnTableCell;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getAllWords;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getKeyValue;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getLastPage;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getParentFieldList;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getTable;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getWholeDocument;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.reArrangeFieldList;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getPageNumber;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getTableColumnSize;
//import static com.aarte.doczy.textractprocessor.util.OcrUtil.getTableKeyValue;
import static com.aarte.doczy.textractprocessor.util.OcrUtil.getLastPage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.aarte.doczy.textractprocessor.bean.ClientConfig;
import com.aarte.doczy.textractprocessor.bean.Field;
import com.aarte.doczy.textractprocessor.bean.KeyValueDetail;
import com.aarte.doczy.textractprocessor.bean.Table;
import com.aarte.doczy.textractprocessor.bean.TableRecordBean;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalProcessingForSingleDocument {

	public static void main(String[] args) throws IOException {

		try (FileReader reader = new FileReader(
				"D:\\developer\\OCR_Project\\my_task\\Ocr_Client_request\\Werner Enterprise\\cg74\\30-10-2020\\Randall Reilley (CG74)\\jsons\\RR Invoices Jan 1 2020 to April 30 2020-2.json")) {
			GetDocumentAnalysisResult analysisResult = new ObjectMapper().readValue(reader,
					GetDocumentAnalysisResult.class);
			ClientConfig clientConfig = null;
			try (FileReader reader1 = new FileReader(
					"D:\\developer\\OCR_Project\\my_task\\Ocr_Client_request\\Werner Enterprise\\cg74\\30-10-2020\\Randall Reilley (CG74)\\processing-rules\\Randall Reilley.json")) {
				clientConfig = new ObjectMapper().readValue(reader1, ClientConfig.class);
			}
			System.out.println(getKeyValue(analysisResult));

		    File f= new
		    File("D:\\developer\\OCR_Project\\my_task\\Ocr_Client_request\\Werner Enterprise\\cg74\\30-10-2020\\Randall Reilley (CG74)\\output.txt"); 
		    FileWriter fw=new FileWriter(f);
		    fw.write(getWholeDocument(analysisResult)); 
		    fw.close();

			List<Field> fieldList = reArrangeFieldList(clientConfig.getClientGroups().get(0).getFields());
			for (Field field : fieldList) {
				List<Field> parentFieldList = getParentFieldList(fieldList, field);
				applyProcessingRules(field, getWholeDocument(analysisResult), getAllWords(analysisResult),
						getKeyValue(analysisResult), "pdfFileName", null, parentFieldList, analysisResult, "", null);

			}
			for (Field field : fieldList) {
				System.out.println(field.getFieldDisplayName() + " =    " + field.getFieldValue());
			}
			/*
			 * System.out.println("before");
			 * System.out.println(getTableKeyValue(analysisResult));
			 * System.out.println("after");
			 */
			
			
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
					if (endPage != -1) {
						List<TableRecordBean> tableRecordBeans = null;
						int recordNumber = 1;
						List<Field> tableFieldList = table.getTableFieldList();
						for (Map.Entry<String, List<TableRecordBean>> page : tableMap.entrySet()) {
							tableRecordBeans = page.getValue();
							
							if ((startPage <= tableRecordBeans.get(0).getPageNo()
									&& tableRecordBeans.get(0).getPageNo() <= endPage)
									&& (noOfColumns == getTableColumnSize(tableRecordBeans, noOfColumns))) {
								System.out.println("Table size : "+getTableColumnSize(tableRecordBeans, noOfColumns));
								Collections.sort(tableRecordBeans);
								for (TableRecordBean tableRecordBean : tableRecordBeans) {
									if (!(startPage == tableRecordBean.getPageNo()
											&& tableRecordBean.getRowIndex() == 1)) {
										if (tableRecordBean.getColumnIndex() == 1) {
											keyValueDetailList = new ArrayList<>();
										}
										//System.out.println("PageNo: "+tableRecordBean.getPageNo());
										for (Field field : tableFieldList) {
											if (field.getTableColumnNumber() == tableRecordBean.getColumnIndex()) {
												keyValueDetailForCell = applyProcessingRulesOnTableCell(
														tableRecordBean.getColumnValue(), field, tableRecordBean.getPageNo());
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
					//System.out.println("tableRecordsMap=" + tableRecordsMap);
					
					if (!tableRecordsMap.isEmpty()) {
						break;
					}
				}
			    for(Entry<Integer, List<KeyValueDetail>> tableRecord : tableRecordsMap.entrySet()) { 
			    	for(KeyValueDetail list : tableRecord.getValue()) {
			    		System.out.println(list.getKey() + " : " +
			    		list.getValue()); 
			    	} 
			    	System.out.println("\n");
			    }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}


