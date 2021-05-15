package com.pdfsoftware.processor.model;

import java.io.Serializable;

public class TableData implements Serializable, Comparable<TableData>{
	private static final long serialVersionUID = -1570985026462046216L;
	private int columnIndex;
	private int rowIndex;
	private int rowSpan;
	private int columnSpan;
	private int pageNo;
	private String columnHeader;
	private String columnValue;
	private String tableId;
	
	/**
	 * @return the columnIndex
	 */
	public int getColumnIndex() {
		return columnIndex;
	}
	/**
	 * @param columnIndex the columnIndex to set
	 */
	public void setColumnIndex(int columnIndex) {
		this.columnIndex = columnIndex;
	}
	/**
	 * @return the rowIndex
	 */
	public int getRowIndex() {
		return rowIndex;
	}
	/**
	 * @param rowIndex the rowIndex to set
	 */
	public void setRowIndex(int rowIndex) {
		this.rowIndex = rowIndex;
	}
	/**
	 * @return the rowSpan
	 */
	public int getRowSpan() {
		return rowSpan;
	}
	/**
	 * @param rowSpan the rowSpan to set
	 */
	public void setRowSpan(int rowSpan) {
		this.rowSpan = rowSpan;
	}
	/**
	 * @return the columnSpan
	 */
	public int getColumnSpan() {
		return columnSpan;
	}
	/**
	 * @param columnSpan the columnSpan to set
	 */
	public void setColumnSpan(int columnSpan) {
		this.columnSpan = columnSpan;
	}
	/**
	 * @return the columnHeader
	 */
	public String getColumnHeader() {
		return columnHeader;
	}
	/**
	 * @param columnHeader the columnHeader to set
	 */
	public void setColumnHeader(String columnHeader) {
		this.columnHeader = columnHeader;
	}
	/**
	 * @return the columnValue
	 */
	public String getColumnValue() {
		return columnValue;
	}
	/**
	 * @param columnValue the columnValue to set
	 */
	public void setColumnValue(String columnValue) {
		this.columnValue = columnValue;
	}
	/**
	 * @return the tableId
	 */
	public String getTableId() {
		return tableId;
	}
	/**
	 * @param tableId the tableId to set
	 */
	public void setTableId(String tableId) {
		this.tableId = tableId;
	}
	/**
	 * @return the pageNo
	 */
	public int getPageNo() {
		return pageNo;
	}
	/**
	 * @param pageNo the pageNo to set
	 */
	public void setPageNo(int pageNo) {
		this.pageNo = pageNo;
	}
	
	@Override
	public String toString() {
		return "TableData [columnIndex=" + columnIndex + ", rowIndex=" + rowIndex + ", rowSpan=" + rowSpan
				+ ", columnSpan=" + columnSpan + ", pageNo=" + pageNo + ", columnHeader=" + columnHeader
				+ ", columnValue=" + columnValue + ", tableId=" + tableId + "]";
	}
	@Override
	public int compareTo(TableData table) {
		if (rowIndex == table.rowIndex)
			return 0;
		else if (rowIndex > table.rowIndex)
			return 1;
		else
			return -1;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((columnHeader == null) ? 0 : columnHeader.hashCode());
		result = prime * result + columnIndex;
		result = prime * result + columnSpan;
		result = prime * result + ((columnValue == null) ? 0 : columnValue.hashCode());
		result = prime * result + rowIndex;
		result = prime * result + rowSpan;
		result = prime * result + ((tableId == null) ? 0 : tableId.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TableData other = (TableData) obj;
		if (columnHeader == null) {
			if (other.columnHeader != null)
				return false;
		} else if (!columnHeader.equals(other.columnHeader))
			return false;
		if (columnIndex != other.columnIndex)
			return false;
		if (columnSpan != other.columnSpan)
			return false;
		if (columnValue == null) {
			if (other.columnValue != null)
				return false;
		} else if (!columnValue.equals(other.columnValue))
			return false;
		if (rowIndex != other.rowIndex)
			return false;
		if (rowSpan != other.rowSpan)
			return false;
		if (tableId == null) {
			if (other.tableId != null)
				return false;
		} else if (!tableId.equals(other.tableId))
			return false;
		return true;
	}
	
}
