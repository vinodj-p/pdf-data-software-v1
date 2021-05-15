package com.pdfsoftware.processor.model;

import java.io.Serializable;

public class KeyValueDetails implements Serializable, Comparable<KeyValueDetails>{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private int displayOrder = 999;
	private Integer key;
	private String value;
	private String comment;
	private double confidence;
	private String fieldId;
	
	public int getDisplayOrder() {
		return displayOrder;
	}
	public void setDisplayOrder(int displayOrderId) {
		this.displayOrder = displayOrderId;
	}
	public Integer getKey() {
		return key;
	}
	public void setKey(Integer key) {
		this.key = key;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public double getConfidence() {
		return confidence;
	}
	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}
	public String getFieldId() {
		return fieldId;
	}
	public void setFieldId(String fieldId) {
		this.fieldId = fieldId;
	}
	
	@Override
	public String toString() {
		return "KeyValueDetails [displayOrder=" + displayOrder + ", key=" + key + ", value=" + value + ", comment="
				+ comment + ", confidence=" + confidence + ", fieldId=" + fieldId + "]";
	}
	
	@Override
	public int compareTo(KeyValueDetails keyValDetails) {
		if(displayOrder == keyValDetails.getDisplayOrder())
			return 0;
		else if(displayOrder > keyValDetails.getDisplayOrder())
			return 1;
		else
			return -1;
	}
}
