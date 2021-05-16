package com.pdfsoftware.test;

import static com.pdfsoftware.processor.util.PdUtil.getTableData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.textract.model.GetDocumentAnalysisResult;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfsoftware.processor.model.KeyValueDetails;
import com.pdfsoftware.processor.model.TableData;
import com.pdfsoftware.processor.util.PdUtil;

public class ProcessTestData {

	public static void main(String[] args) throws JsonParseException, JsonMappingException, FileNotFoundException, IOException {
		
		File inputJson = new File("E:\\Vinod_Project\\github\\pdf-data-software-v1\\output.json");
		ObjectMapper jsonMapper = new ObjectMapper();
    	
		GetDocumentAnalysisResult docAnalysisResults = jsonMapper.readValue(new FileInputStream(inputJson), GetDocumentAnalysisResult.class);
		System.out.println("GetDocumentAnalysisResult Object parsed ..." );

		Map<Integer, Map<Integer, List<KeyValueDetails>>> tableMap = null;
		Map<Integer, List<KeyValueDetails>> tableRecordsMap = null;
		List<KeyValueDetails> keyValueDetailList = null;

        Map<String, List<TableData>> tablesData = getTableData(docAnalysisResults);
        
        List<TableData> tableRecords = null;
		tableMap = new LinkedHashMap<>();
		
		int page = 1;
		int tableCount = 1;
		for (Map.Entry<String, List<TableData>> tableData : tablesData.entrySet()) {
			tableRecordsMap = new LinkedHashMap<>();			
			String tableKey = tableData.getKey();
			tableRecords = tableData.getValue();
			Collections.sort(tableRecords);
			
			int rowIdxFirst = 1;			
			System.out.println("Tables Records for table : "+tableCount+ " " +tableRecords.size());
			int tRows = tableRecords.size();
			
			for(int table = 0; table < tableRecords.size(); table++) {
				KeyValueDetails keyVal = new KeyValueDetails();
				keyValueDetailList = new ArrayList<>();
				
				TableData rowTableIdx = tableRecords.get(table);
				int rowIdx = rowTableIdx.getRowIndex(); //1
				int colIdx = rowTableIdx.getColumnIndex();
				String colValue = rowTableIdx.getColumnValue();
				int tPage = rowTableIdx.getPageNo();
				
				keyVal.setKey(colIdx);
				keyVal.setValue(colValue);
				keyValueDetailList.add(keyVal);
				
				for(int tablej = table+1; tablej < tableRecords.size(); tablej++) {
					
					TableData tdata = tableRecords.get(tablej);
					int rowIdxChild = tdata.getRowIndex();
					int colIdxChild = tdata.getColumnIndex();
					String colChildValue = tdata.getColumnValue();
					int tChildPage = tdata.getPageNo();
					
					if(rowIdx == rowIdxChild) {
						KeyValueDetails keyValChild = new KeyValueDetails();
						keyValChild.setKey(colIdxChild);
						keyValChild.setValue(colChildValue);
						keyValueDetailList.add(keyValChild);
						table++;
						if(null != tableRecords && table == (tableRecords.size()-1))
							tableRecordsMap.put(rowIdxFirst, keyValueDetailList);
						continue;
					}else {
						tableRecordsMap.put(rowIdxFirst, keyValueDetailList);
						rowIdxFirst++;
						break;
					}
				}

			}
			rowIdxFirst = 1;
			System.out.println("records map: "+tableRecordsMap);
			tableMap.put(tableCount++, tableRecordsMap);
			System.out.println("Tables on page :"+tableMap.size());
		}

		PdUtil.populateExcelFromTemplate(tableMap);
		
	}

}
