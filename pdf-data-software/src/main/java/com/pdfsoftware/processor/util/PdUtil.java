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
		System.out.println("In getTableData:: Processing table data...");
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
		System.out.println("Out getTableData:: Processed table data :: TabeMap is: " +tableCellMap.size());
		return tableCellMap;
	}
	
	public static XSSFWorkbook populateExcelFromTemplate(Map<Integer, Map<Integer, List<KeyValueDetails>>> tableMap) throws IOException {
		System.out.println("Generating xml results...");
		XSSFWorkbook xssfWorkbook = null;
		try {
			if (Objects.nonNull(tableMap) && !tableMap.isEmpty()) {
				//InputStream ins = new FileInputStream("E:\\Vinod_Project\\github\\pdf-data-software-v1\\Input.xlsx");
				xssfWorkbook = new XSSFWorkbook();
				
				XSSFSheet sheet = null;
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
				
				
				for (Map.Entry<Integer, Map<Integer, List<KeyValueDetails>>> tableRecords : tableMap.entrySet()) {
					if (null != tableRecords && !tableRecords.getValue().isEmpty()) {
						int tableNo = tableRecords.getKey();
						sheet = xssfWorkbook.createSheet("Table-sheet"+String.valueOf(tableNo));
						for(Map.Entry<Integer, List<KeyValueDetails>> tRowCellsData : tableRecords.getValue().entrySet()) {
							int rowId = tRowCellsData.getKey();
							rowNumber = rowId;
							List<KeyValueDetails> columnDetails = tRowCellsData.getValue();
							
							if(Objects.isNull(tRowCellsData) || columnDetails.isEmpty()) {
								Row dataRow = sheet.createRow(rowNumber++);
								int columnNumber = 0;
								for (KeyValueDetails keyValueDetails : columnDetails) {
									columnNumber = keyValueDetails.getKey();
									cell = dataRow.createCell(columnNumber++);
									cell.setCellValue(keyValueDetails.getValue());
									cell.setCellStyle(style);
								}
							}else{
								Row dataRow = sheet.createRow(rowNumber++);
								int columnNumber = 0;
								for (KeyValueDetails keyValueDetails : columnDetails) {
									columnNumber = keyValueDetails.getKey();
									cell = dataRow.createCell(columnNumber++);
									cell.setCellValue(keyValueDetails.getValue());
									cell.setCellStyle(style);
								}
							}
						}
						
					}else {
						System.out.println("No records to create excel...");
					} 
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		FileOutputStream out = new FileOutputStream(
				new File("E:\\Vinod_Project\\github\\pdf-data-software-v1\\Reports\\output\\Output_AIRCARE_Axis.xlsx"));
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
