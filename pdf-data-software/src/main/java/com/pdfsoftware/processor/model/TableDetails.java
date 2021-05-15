package com.pdfsoftware.processor.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class TableDetails implements Serializable, Comparable<TableDetails>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private List<KeyValueDetails> keyValueDetails;
	private Map<Integer, List<KeyValueDetails>> tablesData;
	
	
	public List<KeyValueDetails> getKeyValueDetails() {
		return keyValueDetails;
	}


	public void setKeyValueDetails(List<KeyValueDetails> keyValueDetails) {
		this.keyValueDetails = keyValueDetails;
	}


	public Map<Integer, List<KeyValueDetails>> getTablesData() {
		return tablesData;
	}


	public void setTablesData(Map<Integer, List<KeyValueDetails>> tablesData) {
		this.tablesData = tablesData;
	}


	@Override
	public int compareTo(TableDetails tableDetails) {
		String fName = null;
		String sName = null;
		for(KeyValueDetails keyValDetails : tableDetails.keyValueDetails) {
			if("1".equalsIgnoreCase(keyValDetails.getFieldId())) {
				sName = keyValDetails.getValue();
			}
		}
		
		for(KeyValueDetails keyValDetails : keyValueDetails) {
			if("1".equalsIgnoreCase(keyValDetails.getFieldId())) {
				fName = keyValDetails.getValue();
			}
		}
			
		return fName.compareTo(sName);
	}


	@Override
	public String toString() {
		return "TableDetails [keyValueDetails=" + keyValueDetails + ", tablesData=" + tablesData + "]";
	}
	
	
}
