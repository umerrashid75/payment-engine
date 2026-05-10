<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="s" uri="/struts-tags" %>
<html>
<head>
    <title>Card Administration</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
        .action-btn { padding: 5px 10px; margin: 2px; cursor: pointer; }
    </style>
</head>
<body>
    <h2>CoreIssuer Card Administration</h2>
    <table>
        <tr>
            <th>Card ID</th>
            <th>Account ID</th>
            <th>Tier</th>
            <th>Status</th>
            <th>Actions</th>
        </tr>
        <s:iterator value="cards">
            <tr>
                <td><s:property value="id"/></td>
                <td><s:property value="account.id"/></td>
                <td><s:property value="tier"/></td>
                <td><s:property value="status"/></td>
                <td>
                    <s:if test="status == 'ACTIVE'">
                        <s:url var="freezeUrl" action="updateCardStatus">
                            <s:param name="cardId" value="id"/>
                            <s:param name="status" value="'FROZEN'"/>
                        </s:url>
                        <a href="<s:property value="#freezeUrl"/>" class="action-btn">Freeze</a>
                        
                        <s:url var="closeUrl" action="updateCardStatus">
                            <s:param name="cardId" value="id"/>
                            <s:param name="status" value="'CLOSED'"/>
                        </s:url>
                        <a href="<s:property value="#closeUrl"/>" class="action-btn">Close</a>
                    </s:if>
                    <s:elseif test="status == 'FROZEN'">
                        <s:url var="unfreezeUrl" action="updateCardStatus">
                            <s:param name="cardId" value="id"/>
                            <s:param name="status" value="'ACTIVE'"/>
                        </s:url>
                        <a href="<s:property value="#unfreezeUrl"/>" class="action-btn">Unfreeze</a>
                        
                        <s:url var="closeUrl" action="updateCardStatus">
                            <s:param name="cardId" value="id"/>
                            <s:param name="status" value="'CLOSED'"/>
                        </s:url>
                        <a href="<s:property value="#closeUrl"/>" class="action-btn">Close</a>
                    </s:elseif>
                </td>
            </tr>
        </s:iterator>
    </table>
</body>
</html>
