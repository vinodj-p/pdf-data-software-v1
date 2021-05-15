package com.pdfsoftware.processor.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.amazonaws.services.textract.model.Relationship;
import com.amazonaws.util.StringUtils;
import com.pdfsoftware.processor.model.TableDetails;
import com.pdfsoftware.processor.model.KeyValueDetails;
import com.pdfsoftware.processor.model.TableData;

public class PdUtil {
	
	public static Map<String, List<TableData>> getTableData(GetDocumentAnalysisResult docAnalysisResult) {
		java.util.List<Block> blocks = docAnalysisResult.getBlocks();

		Map<String, Block> tables = new HashMap<>();
		Map<String, Block> cells = new HashMap<>();
		Map<String, Block> words = new HashMap<>();

		for (Block block : blocks) {
			if ("TABLE".equals(block.getBlockType())) {
				tables.put(block.getId(), block);
			} else if ("CELL".equals(block.getBlockType())) {
				cells.put(block.getId(), block);
			} else if ("WORD".equals(block.getBlockType())) {
				words.put(block.getId(), block);
			}
		}
		List<TableData> tableDataList = new ArrayList<>();
		TableData tableData = null;
		Block tBlock = null;
		Block cBlock = null;
		Block wordBlock = null;
		for (Map.Entry<String, Block> table : tables.entrySet()) {
			tBlock = table.getValue();
			if (null != tBlock) {
				for (Relationship relationshipTable : tBlock.getRelationships()) {
					if (null != relationshipTable && "CHILD".equals(relationshipTable.getType())) {
						for (String cellId : relationshipTable.getIds()) {
							cBlock = cells.get(cellId);
							if (Objects.nonNull(cBlock)) {
								tableData = new TableData();
								tableData.setColumnIndex(cBlock.getColumnIndex());
								tableData.setRowIndex(cBlock.getRowIndex());
								tableData.setRowSpan(cBlock.getRowSpan());
								tableData.setColumnSpan(cBlock.getColumnSpan());
								tableData.setTableId(tBlock.getId());
								StringBuilder cellValue = new StringBuilder("");
								if (Objects.nonNull(cBlock.getRelationships())) {
									for (Relationship relationshipCell : cBlock.getRelationships()) {
										if (null != relationshipCell && "CHILD".equals(relationshipCell.getType())) {
											for (String wordId : relationshipCell.getIds()) {
												wordBlock = words.get(wordId);
												if (Objects.nonNull(wordBlock)) {
													cellValue.append(wordBlock.getText() + " ");
												}
											}
										}
									}
								}
								tableData.setPageNo(cBlock.getPage());
								tableData.setColumnValue(cellValue.toString());
								tableDataList.add(tableData);
							}
						}
					}
				}
			}
		}

		Map<String, List<TableData>> tableCellMap = new HashMap<>();
		List<TableData> tempList = null;
		Collections.sort(tableDataList);

		for (TableData recordList : tableDataList) {
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
	
	public static XSSFWorkbook populateExcelFromTemplate(Map<Integer, List<KeyValueDetails>> tableRecordsMap) throws IOException {
		XSSFWorkbook xssfWorkbook = null;
		try {
			if (Objects.nonNull(tableRecordsMap) && !tableRecordsMap.isEmpty()) {
				InputStream ins = new FileInputStream(
						"E:\\Vinod_Project\\github\\pdf-data-software-v1\\Input.xlsx");
				
				xssfWorkbook = new XSSFWorkbook(ins);
				List<KeyValueDetails> keyValueList = null;
				XSSFSheet sheet = xssfWorkbook.getSheetAt(0);
				int rowNumber = 1;
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
				for (Map.Entry<Integer, List<KeyValueDetails>> records : tableRecordsMap.entrySet()) {
					
					if (Objects.isNull(records)
							|| records.getValue().isEmpty()) {
						keyValueList = records.getValue();
						Row dataRow = sheet.createRow(rowNumber++);
						int dataColumnNumber = 0;
						for (KeyValueDetails keyValueDetails : keyValueList) {
							cell = dataRow.createCell(dataColumnNumber++);
							cell.setCellValue(keyValueDetails.getValue());
							cell.setCellStyle(style);
						}
					} else {
						int dataColumnNumber = 0;
						keyValueList = records.getValue();
						if (isValuePresent(keyValueList)) {
							Row dataRow = sheet.createRow(rowNumber++);
							for (KeyValueDetails keyValueDetailTableRecord : keyValueList) {
								cell = dataRow.createCell(dataColumnNumber++);
								cell.setCellValue(keyValueDetailTableRecord.getValue());
								cell.setCellStyle(style);
							}
						}
					}
				}
			} else {
					System.out.println("No records to create excel...");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		FileOutputStream out = new FileOutputStream(
				new File("E:\\Vinod_Project\\github\\pdf-data-software-v1\\Output.xlsx"));
		xssfWorkbook.write(out);
		out.close();
		System.out.println("excel created successfully on local disk.");
		return xssfWorkbook;
	}

	private static boolean isValuePresent(List<KeyValueDetails> keyValueTableData) {
		boolean isEmpty = false;
		for(KeyValueDetails details : keyValueTableData) {
			if(!StringUtils.isNullOrEmpty(details.getValue()))
				isEmpty = true;
		}
		return isEmpty;
	}
	
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
