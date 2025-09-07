package com.galaxytx.core.client.external;

import java.util.Map;

/**
 * 事务请求体
 */
public class TransactionRequest {
    private String xid;
    private long branchId;
    private String operation;
    private long timestamp;
    private String serviceGroup;
    private Map<String, Object> parameters;

    // Getter和Setter方法
    public String getXid() { return xid; }
    public void setXid(String xid) { this.xid = xid; }

    public long getBranchId() { return branchId; }
    public void setBranchId(long branchId) { this.branchId = branchId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getServiceGroup() { return serviceGroup; }
    public void setServiceGroup(String serviceGroup) { this.serviceGroup = serviceGroup; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
