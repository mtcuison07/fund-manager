/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.rmj.fund.manager.base;

import com.sun.rowset.CachedRowSetImpl;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.RowSetMetaData;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.MySQLAESCrypt;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.StringUtil;
import org.rmj.appdriver.agentfx.ui.showFXDialog;
import org.rmj.appdriver.constants.EditMode;
import org.rmj.appdriver.constants.TransactionStatus;
import org.rmj.appdriver.constants.UserRight;
import org.rmj.fund.manager.parameters.IncentiveBankInfo;

/**
 *
 * @author User
 */
public class IncentiveReports {

    private final String FINANCE = "028";
    private final String AUDITOR = "034";
    private final String COLLECTION = "022";
    private final String MAIN_OFFICE = "M001»M0W1";

    private final String DEBUG_MODE = "app.debug.mode";
    private final String REQUIRE_CSS = "app.require.css.approval";
    private final String REQUIRE_CM = "app.require.cm.approval";
    private final String REQUIRE_BANK_ON_APPROVAL = "app.require.bank.on.approval";

    private final GRider p_oApp;
    private final boolean p_bWithParent;

    private final IncentiveBankInfo p_oBankInfo;

    private String p_sBranchCd;
    private int p_nEditMode;
    private int p_nTranStat;
    private boolean isEmpInctive;

    private String p_sMessage;
    private boolean p_bWithUI = true;

    private CachedRowSet p_oBranch;
    private CachedRowSet p_oMaster;
    private CachedRowSet p_oDetail;
    private CachedRowSet p_oDetailVal;
    private CachedRowSet p_oDetailEmp;
    private CachedRowSet p_oDetailCateg;
    private CachedRowSet p_oAllctn;
    private CachedRowSet p_oAllctn_Emp;
    private CachedRowSet p_oDedctn;
    private CachedRowSet p_oDedctn_Emp;
    private CachedRowSet p_oEmployee;

    private LMasDetTrans p_oListener;

    public IncentiveReports(GRider foApp, String fsBranchCd, boolean fbWithParent) {
        p_oApp = foApp;
        p_sBranchCd = fsBranchCd;
        p_bWithParent = fbWithParent;

        if (p_sBranchCd.isEmpty()) {
            p_sBranchCd = p_oApp.getBranchCode();
        }

        p_oBankInfo = new IncentiveBankInfo(p_oApp, p_sBranchCd, true);
        p_oBankInfo.setRecordStat(1);

        loadConfig();

        p_nTranStat = 0;
        p_nEditMode = EditMode.UNKNOWN;
    }

    public void setTranStat(int fnValue) {
        p_nTranStat = fnValue;
    }

    public void setListener(LMasDetTrans foValue) {
        p_oListener = foValue;
    }

    public void setWithUI(boolean fbValue) {
        p_bWithUI = fbValue;
    }

    public int getEditMode() {
        return p_nEditMode;
    }

    public IncentiveBankInfo getBankInfo(String fsEmployID) throws SQLException {
        if (p_oBankInfo.OpenRecord(fsEmployID)) {
            return p_oBankInfo;
        } else {
            return null;
        }
    }

    public boolean OpenTransactionEmployee(String fsValue) throws SQLException {

        if (p_oApp == null) {
            p_sMessage = "Application driver is not set.";
            return false;
        }

        createDetailEmployee();
        createDetailEmployeeNew();
        isEmpInctive = true;
        p_sMessage = "";
        String lsSQL;
        ResultSet loRS;
        RowSetFactory factory = RowSetProvider.newFactory();
        String lsCondition = "";
        String lsCondition1 = "";

        if (p_oEmployee != null) {
//            lsCondition = lsCondition + " AND a.sEmployID = " +  SQLUtil.toSQL(getEmployee("sEmployID"));  

            lsCondition = " AND a.sTransNox LIKE " + SQLUtil.toSQL(getBranch("sBranchCd") + "%");
            lsCondition1 = " AND a.sTransNox LIKE " + SQLUtil.toSQL(getBranch("sBranchCd") + "%");
        }
        if (p_oBranch != null) {
//            lsCondition = lsCondition + " AND f.sBranchCD = " +  SQLUtil.toSQL(getBranch("sBranchCd"));
            lsCondition = " AND  LEFT(a.sTransNox, 4) = LEFT(b.sEmployID, 4)";
            lsCondition1 = " AND  LEFT(a.sTransNox, 4) = LEFT(b.sEmployID, 4)";
        }

        if (!fsValue.isEmpty()) {
            lsCondition = lsCondition + " AND a.sMonthxxx = " + SQLUtil.toSQL(fsValue);
            lsCondition1 = lsCondition + " AND a.sMonthxxx = " + SQLUtil.toSQL(fsValue);
        }

        lsCondition = lsCondition + " GROUP BY b.sEmployID,a.sMonthxxx,a.sTransNox";
        lsSQL = getSQ_Detail() + lsCondition;
        loRS = p_oApp.executeQuery(lsSQL);
        p_oDetailEmp = factory.createCachedRowSet();
        p_oDetailEmp.populate(loRS);
        MiscUtil.close(loRS);
        computeEmpTotalIncentiveAmount();

        lsCondition1 = lsCondition1 + " GROUP BY b.sEmployID,a.sMonthxxx";
        lsSQL = getSQ_Detail() + lsCondition1;
        System.out.println(" OPenEmployee = " + lsSQL);
        loRS = p_oApp.executeQuery(lsSQL);
        p_oDetailVal = factory.createCachedRowSet();
        p_oDetailVal.populate(loRS);
        computeEmpTotalIncentiveAmountNew();
        MiscUtil.close(loRS);
        p_nEditMode = EditMode.READY;
        return true;
    }

    private void computeEmpTotalIncentiveAmountNew() throws SQLException {
        int lnDetRow = getNewEmpCount();
        int lnIncRow;
        int lnAlcRow;

        int lnCtr1, lnCtr2, lnCtr3;
        double lnTotalAmt, lnPercentx, lnAllcAmtx, lnIncentve, lnDeductnx;
        double lnTotalInc, lnTotalDed;
        double transTotal = 0.0;

        for (lnCtr1 = 1; lnCtr1 <= lnDetRow; lnCtr1++) {
            p_oDetailVal.absolute(lnCtr1);

            lnTotalInc = 0.00;
            lnTotalDed = 0.00;
            lnTotalAmt = 0.00;
            String lsSQL;
            String lsCondition;
            ResultSet loRS;
            RowSetFactory factory = RowSetProvider.newFactory();

            for (lnCtr2 = 1; lnCtr2 <= getEmpCount(); lnCtr2++) {

                p_oDetailEmp.absolute(lnCtr2);
//                 p_oAllctn.getString("sInctveCD").equals(p_oAllctn_Emp.getString("sInctveCD")
                if (p_oDetailEmp.getString("sEmployID").equals(p_oDetailVal.getString("sEmployID"))) {
                    lnTotalInc += Double.parseDouble(p_oDetailEmp.getString("xIncentve"));
                    lnTotalDed += Double.parseDouble(p_oDetailEmp.getString("xDeductnx"));
                    lnTotalAmt += DecryptAmount(p_oDetailEmp.getString("nTotalAmt"));
                }
            }

            System.out.println(p_oDetailVal.getString("sEmployID") + " total = " + lnTotalAmt);
            p_oDetailVal.updateString("xIncentve", String.valueOf(lnTotalInc));
            p_oDetailVal.updateString("xDeductnx", String.valueOf(lnTotalDed));
            p_oDetailVal.updateString("nTotalAmt", EncryptAmount(lnTotalAmt));
            transTotal = transTotal + (lnTotalInc - lnTotalDed);
            p_oDetailVal.updateRow();

        }

    }

    public boolean OpenTransactionCategory(String fsValue) throws SQLException {

        if (p_oApp == null) {
            p_sMessage = "Application driver is not set.";
            return false;
        }
        createDetailCategory();
        p_sMessage = "";
        String lsSQL;
        ResultSet loRS;
        RowSetFactory factory = RowSetProvider.newFactory();
        String lsCondition = "";
        String lsCondition1 = "";
        String lsCondition2 = "";
        if (p_oBranch != null) {
            lsCondition = " AND a.sTransNox LIKE " + SQLUtil.toSQL(getBranch("sBranchCd") + "%");
        } else {
            lsCondition = " AND  LEFT(a.sTransNox, 4) = LEFT(b.sEmployID, 4)";
        }

        if (!fsValue.isEmpty()) {
            lsCondition = lsCondition + " AND a.sMonthxxx = " + SQLUtil.toSQL(fsValue);
        }
        lsCondition = lsCondition;

        lsSQL = getSQ_EmployeeDetail() + lsCondition;
        System.out.println(lsSQL);
        loRS = p_oApp.executeQuery(lsSQL);
        p_oDetailCateg = factory.createCachedRowSet();
        p_oDetailCateg.populate(loRS);
        MiscUtil.close(loRS);
        computeEmpTotalIncentivesAmount();
        p_nEditMode = EditMode.READY;
        return true;
    }

    public boolean OpenTransactionMaster(String fsValue) throws SQLException {

        if (p_oApp == null) {
            p_sMessage = "Application driver is not set.";
            return false;
        }
        createDetail();
        p_sMessage = "";

        if (System.getProperty(DEBUG_MODE).equals("0")) {
            if (Integer.valueOf(p_oApp.getEmployeeLevel()) < 1) {
                p_sMessage = "Your employee level is not authorized to use this transaction.";
                return false;
            }

            if (p_oApp.getUserLevel() < UserRight.SUPERVISOR) {
                p_sMessage = "Your account level is not authorized to use this transaction.";
                return false;
            }
        }

        String lsSQL;
        ResultSet loRS;
        RowSetFactory factory = RowSetProvider.newFactory();
        String lsCondition = lsCondition = " AND LEFT(sTransNox, 4) = c.sBranchCD";
        String lsCondition2 = "";
        if (p_oBranch != null) {
            lsCondition = lsCondition + " AND c.sBranchCD = " + SQLUtil.toSQL(getBranch("sBranchCd"));
        }

        if (!fsValue.isEmpty()) {
            lsCondition = lsCondition + " AND a.sMonthxxx = " + SQLUtil.toSQL(fsValue);
        }
        lsSQL = getSQ_Master() + lsCondition + " ORDER BY xBranchNm, a.sMonthxxx";
        loRS = p_oApp.executeQuery(lsSQL);
        p_oMaster = factory.createCachedRowSet();
        p_oMaster.populate(loRS);
        MiscUtil.close(loRS);
        computeEmpTotalIncentiveAmountMaster();
        p_nEditMode = EditMode.READY;
        return true;
    }

    public Double OpenToTalMaster(int fnRow, String fsTransNox) throws SQLException {
        p_nEditMode = EditMode.UNKNOWN;

        if (p_oApp == null) {
            p_sMessage = "Application driver is not set.";
            return 0.0;
        }

        p_sMessage = "";

        if (System.getProperty(DEBUG_MODE).equals("0")) {
            if (Integer.valueOf(p_oApp.getEmployeeLevel()) < 1) {
                p_sMessage = "Your employee level is not authorized to use this transaction.";
                return 0.0;
            }

            if (p_oApp.getUserLevel() < UserRight.SUPERVISOR) {
                p_sMessage = "Your account level is not authorized to use this transaction.";
                return 0.0;
            }
        }

        String lsSQL;
        ResultSet loRS;
        RowSetFactory factory = RowSetProvider.newFactory();

        //open detail
        lsSQL = MiscUtil.addCondition(getSQ_MasterDetail(), "a.sTransNox = " + SQLUtil.toSQL(fsTransNox));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oDetail = factory.createCachedRowSet();
        p_oDetail.populate(loRS);
        MiscUtil.close(loRS);
        //open incentive
        lsSQL = MiscUtil.addCondition(getSQ_Detail_Allocation(), "a.sTransNox = " + SQLUtil.toSQL(fsTransNox));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oAllctn = factory.createCachedRowSet();
        p_oAllctn.populate(loRS);
        MiscUtil.close(loRS);

        //open incentive employee allocation
        lsSQL = MiscUtil.addCondition(getSQ_Detail_Allocation_Emp(), "a.sTransNox = " + SQLUtil.toSQL(fsTransNox));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oAllctn_Emp = factory.createCachedRowSet();
        p_oAllctn_Emp.populate(loRS);
        MiscUtil.close(loRS);

        //open deductions
        lsSQL = MiscUtil.addCondition(getSQ_Detail_Deduction(), "sTransNox = " + SQLUtil.toSQL(fsTransNox));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oDedctn = factory.createCachedRowSet();
        p_oDedctn.populate(loRS);
        MiscUtil.close(loRS);

        //open deductions employee alloction
        lsSQL = MiscUtil.addCondition(getSQ_Detail_Deduction_Emp(), "a.sTransNox = " + SQLUtil.toSQL(fsTransNox));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oDedctn_Emp = factory.createCachedRowSet();
        p_oDedctn_Emp.populate(loRS);
        MiscUtil.close(loRS);

        computeEmpTotalIncentiveAmount();
        double transTotal = 0;
        for (int x = 1; x <= getItemCount(); x++) {
            transTotal = transTotal + Double.parseDouble(getDetail(x, "nTotalAmt").toString());
        }

        return transTotal;
    }

    public boolean OpenBranch(String fsBranch) throws SQLException {
        p_nEditMode = EditMode.UNKNOWN;

        if (p_oApp == null) {
            p_sMessage = "Application driver is not set.";
            return false;
        }

        p_sMessage = "";

        String lsSQL;
        ResultSet loRS;
        RowSetFactory factory = RowSetProvider.newFactory();

        //open master
        lsSQL = MiscUtil.addCondition(getSQ_Branch(), "sBranchCd = " + SQLUtil.toSQL(fsBranch));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oBranch = factory.createCachedRowSet();
        p_oBranch.populate(loRS);
        MiscUtil.close(loRS);

        p_nEditMode = EditMode.READY;

        return true;
    }

    public int getDeductionCount() throws SQLException {
        p_oDedctn.last();
        return p_oDedctn.getRow();
    }

    public int getDeductionEmployeeAllocationCount() throws SQLException {
        p_oDedctn_Emp.last();
        return p_oDedctn_Emp.getRow();
    }

    public int getItemCount() throws SQLException {
        p_oDetail.last();
        return p_oDetail.getRow();
    }

    public int getEmpCount() throws SQLException {
        p_oDetailEmp.last();
        return p_oDetailEmp.getRow();
    }

    public int getNewEmpCount() throws SQLException {
        p_oDetailVal.last();
        return p_oDetailVal.getRow();
    }

    public int getCategoryCount() throws SQLException {
        p_oDetailCateg.last();
        return p_oDetailCateg.getRow();
    }

    public int getIncentiveCount() throws SQLException {
        p_oAllctn.last();
        return p_oAllctn.getRow();
    }

    public int getIncentiveEmployeeAllocationCount() throws SQLException {
        p_oAllctn_Emp.last();
        return p_oAllctn_Emp.getRow();
    }

    public Object getIncentiveInfo(int fnRow, int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getIncentiveCount() == 0 || fnRow > getIncentiveCount()) {
            return null;
        }

        p_oAllctn.absolute(fnRow);
        switch (fnIndex) {
            case 7://nInctvAmt
                return DecryptAmount(p_oAllctn.getString(fnIndex));
            case 101://xAllocPer
                switch (p_oAllctn.getString("xByPercnt")) {
                    case "0": //no
                        return (getAllocatedIncentive(fnRow, "0") / DecryptAmount(p_oAllctn.getString("nInctvAmt"))) * 100;
                    case "1": //yes
                        return getAllocatedIncentive(fnRow, "1");
                    case "2":
                        return (getAllocatedIncentive(fnRow, "0") + ((DecryptAmount(p_oAllctn.getString("nInctvAmt")) - getAllocatedIncentive(fnRow, p_oAllctn.getString("xByPercnt"))) * getAllocatedIncentive(fnRow, "1") / 100)) / DecryptAmount(p_oAllctn.getString("nInctvAmt")) * 100;
                    default:
                        return 0.00;
                }
            case 102://xAllocAmt
                switch (p_oAllctn.getString("xByPercnt")) {
                    case "0": //no
                        return getAllocatedIncentive(fnRow, p_oAllctn.getString("xByPercnt"));
                    case "1":
                        return getAllocatedIncentive(fnRow, p_oAllctn.getString("xByPercnt")) * DecryptAmount(p_oAllctn.getString("nInctvAmt")) / 100;
                    case "2": //combi
                        return getAllocatedIncentive(fnRow, p_oAllctn.getString("xByPercnt"))
                                + ((DecryptAmount(p_oAllctn.getString("nInctvAmt")) - getAllocatedIncentive(fnRow, p_oAllctn.getString("xByPercnt"))) * getAllocatedIncentive(fnRow, "1") / 100);

                    default:
                        return 0.00;
                }
            default:
                return p_oAllctn.getObject(fnIndex);
        }
    }

    public Object getIncentiveInfo(int fnRow, String fsIndex) throws SQLException {
        return getIncentiveInfo(fnRow, getColumnIndex(p_oAllctn, fsIndex));
    }

    public void setIncentiveInfo(int fnRow, int fnIndex, Object foValue) throws SQLException {
        if (getIncentiveCount() == 0 || fnRow > getIncentiveCount()) {
            return;
        }

        p_oAllctn.absolute(fnRow);
        switch (fnIndex) {
            case 3: //nQtyGoalx
            case 4: //nQtyActlx
                p_oAllctn.updateInt(fnIndex, 0);

                if (StringUtil.isNumeric(String.valueOf(foValue))) {
                    p_oAllctn.updateInt(fnIndex, (int) foValue);
                }

                p_oAllctn.updateRow();
                break;
            case 5: //nAmtGoalx
            case 6: //nAmtActlx
                p_oAllctn.updateDouble(fnIndex, 0.00);

                if (StringUtil.isNumeric(String.valueOf(foValue))) {
                    p_oAllctn.updateDouble(fnIndex, (double) foValue);
                }

                p_oAllctn.updateRow();
                break;
            case 7: //nInctvAmt
                if (StringUtil.isNumeric(String.valueOf(foValue))) {
                    p_oAllctn.updateString(fnIndex, EncryptAmount((double) foValue));
                } else {
                    p_oAllctn.updateString(fnIndex, EncryptAmount(0.00));
                }

                p_oAllctn.updateRow();

                computeEmpTotalIncentiveAmount();
                break;
            case 8: //sRemarksx
            case 9: //xInctvNme
            case 10: //xByPercnt
                p_oAllctn.updateString(fnIndex, (String) foValue);
                p_oAllctn.updateRow();
        }

    }

    public void setIncentiveInfo(int fnRow, String fsIndex, Object foValue) throws SQLException {
        setIncentiveInfo(fnRow, getColumnIndex(p_oAllctn, fsIndex), foValue);
    }

    public Object getIncentiveEmployeeAllocationInfo(int fnIndex, String fsInctveCD, String fsEmployID) throws SQLException {
        if (getItemCount() == 0 || getIncentiveCount() == 0) {
            return null;
        }

        //find record based on incentive code and employee id
        int lnRow = getIncentiveEmployeeAllocationCount();

        for (int lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            p_oAllctn_Emp.absolute(lnCtr);

            if (fsInctveCD.equals(p_oAllctn_Emp.getString("sInctveCD"))
                    && fsEmployID.equals(p_oAllctn_Emp.getString("sEmployID"))) {

                switch (fnIndex) {
                    case 5: //nAllcAmtx
                        return DecryptAmount(p_oAllctn_Emp.getString(fnIndex));
                    default:
                        return p_oAllctn_Emp.getObject(fnIndex);
                }
            }
        }

        return null;
    }

    public Object getIncentiveEmployeeAllocationInfo(String fsIndex, String fsInctveCD, String fsEmployID) throws SQLException {
        return getIncentiveEmployeeAllocationInfo(getColumnIndex(p_oAllctn_Emp, fsIndex), fsInctveCD, fsEmployID);
    }

    public void setDeductionEmployeeAllocationInfo(int fnIndex, int fnEntryNox, String fsEmployID, Object foValue) throws SQLException {
        if (getItemCount() == 0 || getDeductionCount() == 0 || fnIndex == 0) {
            return;
        }

        int lnCtr;

        //find record based on deduction entry no and employee id
        int lnRow = getDeductionEmployeeAllocationCount();

        for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            p_oDedctn_Emp.absolute(lnCtr);

            if (fnEntryNox == p_oDedctn_Emp.getInt("nEntryNox")
                    && fsEmployID.equals(p_oDedctn_Emp.getString("sEmployID"))) {

                switch (fnIndex) {
                    case 4: //nAllcPerc
                        p_oDedctn_Emp.updateDouble(fnIndex, 0.00);

                        if (StringUtil.isNumeric(String.valueOf(foValue))) {
                            p_oDedctn_Emp.updateDouble(fnIndex, (double) foValue);
                        }

                        break;
                    case 5: //nAllcAmtx
                        if (StringUtil.isNumeric(String.valueOf(foValue))) {
                            p_oDedctn_Emp.updateString(fnIndex, EncryptAmount((double) foValue));
                        } else {
                            p_oDedctn_Emp.updateString(fnIndex, EncryptAmount(0.00));
                        }

                        break;
                    default:
                        p_oDedctn_Emp.setObject(fnIndex, (String) foValue);
                }

                p_oDedctn_Emp.updateRow();
                computeEmpTotalIncentiveAmount();
                break;
            }
        }
    }

    public void setDeductionEmployeeAllocationInfo(String fsIndex, int fnEntryNox, String fsEmployID, Object foValue) throws SQLException {
        setDeductionEmployeeAllocationInfo(getColumnIndex(p_oDedctn_Emp, fsIndex), fnEntryNox, fsEmployID, foValue);
    }

    public void setIncentiveEmployeeAllocationInfo(int fnIndex, String fsInctveCD, String fsEmployID, Object foValue) throws SQLException {
        setIncentiveEmployeeAllocationInfo(p_oAllctn_Emp.getMetaData().getColumnLabel(fnIndex), fsInctveCD, fsEmployID, foValue);
    }

    public void resetIncentiveEmployeeAllocation(String fsInctveCD) throws SQLException {
        if (getItemCount() == 0 || getIncentiveCount() == 0) {
            return;
        }

        int lnRow = getIncentiveEmployeeAllocationCount();

        for (int lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            if (fsInctveCD.equals(p_oAllctn_Emp.getString("sInctveCD"))) {
                p_oAllctn_Emp.absolute(lnCtr);
                p_oAllctn_Emp.updateDouble("nAllcPerc", 0.00);
                p_oAllctn_Emp.updateString("nAllcAmtx", EncryptAmount(0.00));
                p_oAllctn_Emp.updateRow();
            }
        }
        computeEmpTotalIncentiveAmount();
    }

    public void resetDeductionEmployeeAllocation(int fnEntryNox) throws SQLException {
        if (getItemCount() == 0 || getDeductionCount() == 0) {
            return;
        }

        int lnCtr;

        //find record based on deduction entry no and employee id
        int lnRow = getDeductionEmployeeAllocationCount();

        for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            p_oDedctn_Emp.absolute(lnCtr);

            if (fnEntryNox == p_oDedctn_Emp.getInt("nEntryNox")) {
                p_oDedctn_Emp.absolute(lnCtr);
                p_oDedctn_Emp.updateDouble("nAllcPerc", 0.00);
                p_oDedctn_Emp.updateString("nAllcAmtx", EncryptAmount(0.00));
                p_oDedctn_Emp.updateRow();
            }
        }
        computeEmpTotalIncentiveAmount();
    }

    public void setIncentiveEmployeeAllocationInfo(String fsIndex, String fsInctveCD, String fsEmployID, Object foValue) throws SQLException {
        if (getItemCount() == 0 || getIncentiveCount() == 0) {
            return;
        }

        int lnCtr;
        int lnRow = getIncentiveCount();

        for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            p_oAllctn.absolute(lnCtr);
            if (fsInctveCD.equals(p_oAllctn.getString("sInctveCD"))) {
                break;
            }
        }

        //find record based on incentive code and employee id
        lnRow = getIncentiveEmployeeAllocationCount();

        for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            p_oAllctn_Emp.absolute(lnCtr);

            if (fsInctveCD.equals(p_oAllctn_Emp.getString("sInctveCD"))
                    && fsEmployID.equals(p_oAllctn_Emp.getString("sEmployID"))) {

                switch (fsIndex) {
                    case "nAllcPerc":
                        if (p_oAllctn.getString("xByPercnt").equals("1")
                                || p_oAllctn.getString("xByPercnt").equals("2")) {

                            p_oAllctn_Emp.updateDouble(fsIndex, 0.00);

                            if (StringUtil.isNumeric(String.valueOf(foValue))) {
                                p_oAllctn_Emp.updateDouble(fsIndex, (double) foValue);
                            }
                        }
                        break;
                    case "nAllcAmtx":
                        if (p_oAllctn.getString("xByPercnt").equals("0")
                                || p_oAllctn.getString("xByPercnt").equals("2")) {

                            if (StringUtil.isNumeric(String.valueOf(foValue))) {
                                p_oAllctn_Emp.updateString(fsIndex, EncryptAmount((double) foValue));
                            } else {
                                p_oAllctn_Emp.updateString(fsIndex, EncryptAmount(0.00));
                            }
                        }
                        break;
                    case "sRemarksx":
                        p_oAllctn_Emp.setString(fsIndex, (String) foValue);
                }

                p_oAllctn_Emp.updateRow();
                computeEmpTotalIncentiveAmount();
                break;
            }
        }
    }

    public Object getDeductionEmployeeAllocationInfo(int fnIndex, int fnEntryNox, String fsEmployID) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getItemCount() == 0 || getDeductionCount() == 0) {
            return null;
        }

        //find record based on incentive code and employee id
        int lnRow = getDeductionEmployeeAllocationCount();

        for (int lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            p_oDedctn_Emp.absolute(lnCtr);

            if (fnEntryNox == p_oDedctn_Emp.getInt("nEntryNox")
                    && fsEmployID.equals(p_oDedctn_Emp.getString("sEmployID"))) {

                switch (fnIndex) {
                    case 5: //nAllcAmtx
                        return DecryptAmount(p_oDedctn_Emp.getString(fnIndex));
                    default:
                        return p_oDedctn_Emp.getObject(fnIndex);
                }
            }
        }

        return null;
    }

    public Object getDeductionEmployeeAllocationInfo(String fsIndex, int fnEntryNox, String fsEmployID) throws SQLException {
        return getDeductionEmployeeAllocationInfo(getColumnIndex(p_oDedctn_Emp, fsIndex), fnEntryNox, fsEmployID);
    }

    public Object getDeductionInfo(int fnRow, int fnIndex) throws SQLException {
        if (fnRow == 0 || getDeductionCount() == 0) {
            return null;
        }

        p_oDedctn.absolute(fnRow);
        switch (fnIndex) {
            case 4: //nDedctAmt
                return DecryptAmount(p_oDedctn.getString("nDedctAmt"));
            case 101: //xAllocPer
                return ((getAllocatedDeduction(fnRow, "0")
                        + ((DecryptAmount(p_oDedctn.getString("nDedctAmt")) - getAllocatedDeduction(fnRow, "0")) * getAllocatedDeduction(fnRow, "1") / 100)) / DecryptAmount(p_oDedctn.getString("nDedctAmt")) * 100);
            case 102: //xAllocAmt
                return getAllocatedDeduction(fnRow, "0")
                        + ((DecryptAmount(p_oDedctn.getString("nDedctAmt")) - getAllocatedDeduction(fnRow, "0")) * getAllocatedDeduction(fnRow, "1") / 100);
            default:
                return p_oDedctn.getObject(fnIndex);
        }
    }

    public Object getDeductionInfo(int fnRow, String fsIndex) throws SQLException {
        switch (fsIndex) {
            case "xAllocPer":
                return 101;
            case "xAllocAmt":
                return 102;
            default:
                return getDeductionInfo(fnRow, getColumnIndex(p_oDedctn, fsIndex));
        }
    }

    public void setDeductionInfo(int fnRow, int fnIndex, Object foValue) throws SQLException {
        if (getDeductionCount() == 0 || fnRow == 0) {
            return;
        }

        p_oDedctn.absolute(fnRow);

        switch (fnIndex) {
            case 4: //nDedctAmt
                if (StringUtil.isNumeric(String.valueOf(foValue))) {
                    p_oDedctn.updateObject(fnIndex, EncryptAmount((double) foValue));
                } else {
                    p_oDedctn.updateObject(fnIndex, EncryptAmount(0.00));
                }

                p_oDedctn.updateRow();

                computeEmpTotalIncentiveAmount();
                break;
            default:
                p_oDedctn.updateObject(fnIndex, foValue);
                break;
        }
    }

    public void setDeductionInfo(int fnRow, String fsIndex, Object foValue) throws SQLException {
        setDeductionInfo(fnRow, getColumnIndex(p_oDedctn, fsIndex), foValue);
    }

    public Object getDetail(int fnRow, int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getItemCount() == 0 || fnRow > getItemCount()) {
            return null;
        }

        p_oDetail.absolute(fnRow);
        switch (fnIndex) {
            case 4://nTotalAmt
                return DecryptAmount(p_oDetail.getString(fnIndex));
            default:
                return p_oDetail.getObject(fnIndex);
        }
    }

    public Object getDetail(int fnRow, String fsIndex) throws SQLException {
        return getDetail(fnRow, getColumnIndex(p_oDetail, fsIndex));
    }

    public Object getDetailEmployee(int fnRow, int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getEmpCount() == 0 || fnRow > getEmpCount()) {
            return null;
        }

        p_oDetailEmp.absolute(fnRow);
        switch (fnIndex) {
            case 6://nTotalAmt
                return DecryptAmount(p_oDetailEmp.getString(fnIndex));
            default:
                return p_oDetailEmp.getObject(fnIndex);
        }
    }

    public Object getDetailEmployee(int fnRow, String fsIndex) throws SQLException {
        return getDetailEmployee(fnRow, getColumnIndex(p_oDetailEmp, fsIndex));
    }

    public Object getNewDetailEmployee(int fnRow, int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getNewEmpCount() == 0 || fnRow > getNewEmpCount()) {
            return null;
        }

        p_oDetailVal.absolute(fnRow);
        switch (fnIndex) {
            case 6://nTotalAmt
                return DecryptAmount(p_oDetailVal.getString(fnIndex));
            default:
                return p_oDetailVal.getObject(fnIndex);
        }
    }

    public Object getNewDetailEmployee(int fnRow, String fsIndex) throws SQLException {
        return getNewDetailEmployee(fnRow, getColumnIndex(p_oDetailVal, fsIndex));
    }

    public Object getDetailCategory(int fnRow, int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getCategoryCount() == 0 || fnRow > getCategoryCount()) {
            return null;
        }

        p_oDetailCateg.absolute(fnRow);
        switch (fnIndex) {
            case 6://xIncentive
            case 8://xInctvAmt
//                if(p_oDetailCateg.getString(fnIndex).equalsIgnoreCase("0.0")){
//                    return p_oDetailCateg.getObject(fnIndex);
//                }else{
//                    return DecryptAmount(p_oDetailCateg.getString(fnIndex));
//                }
                return DecryptAmount(p_oDetailCateg.getString(fnIndex));
            default:
                return p_oDetailCateg.getObject(fnIndex);
        }
    }

    public Object getDetailCategory(int fnRow, String fsIndex) throws SQLException {
        return getDetailCategory(fnRow, getColumnIndex(p_oDetailCateg, fsIndex));
    }

    public int getItemMasterCount() throws SQLException {
        p_oMaster.last();
        return p_oMaster.getRow();
    }

    public Object getMaster(int fnRow, int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }
        if (getItemMasterCount() == 0 || fnRow > getItemMasterCount()) {
            return null;
        }

        p_oMaster.absolute(fnRow);
        return p_oMaster.getObject(fnIndex);
    }

    public Object getMaster(int fnRow, String fsIndex) throws SQLException {
        return getMaster(fnRow, getColumnIndex(p_oMaster, fsIndex));
    }

    public void setMaster(int fnRow, int fnIndex, Object foValue) throws SQLException {
        switch (fnIndex) {
            case 3://sDeptIDxx
                searchDepartment(fnRow, (String) foValue, true);
                break;
            case 4://sMonthxxx
            case 5://sRemarksx
            case 16://xBranchNm
            case 17://xDeptName
                p_oMaster.updateString(fnIndex, (String) foValue);
                p_oMaster.updateRow();

                if (p_oListener != null) {
                    p_oListener.MasterRetreive(fnIndex, p_oMaster.getString(fnIndex));
                }
                break;
        }
    }

    public Object getBranch(int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }

        p_oBranch.first();
        return p_oBranch.getObject(fnIndex);
    }

    public Object getBranch(String fsIndex) throws SQLException {
        return getBranch(getColumnIndex(p_oBranch, fsIndex));
    }

    public void setBranch() {
        p_oBranch = null;
    }

    public Object getEmployee(int fnIndex) throws SQLException {
        if (fnIndex == 0) {
            return null;
        }

        p_oEmployee.first();
        return p_oEmployee.getObject(fnIndex);
    }

    public Object getEmployee(String fsIndex) throws SQLException {
        return getEmployee(getColumnIndex(p_oEmployee, fsIndex));
    }

    public void setEmployee() {
        p_oEmployee = null;
    }

    public boolean searchDepartment(int fnRow, String fsValue, boolean fbByCode) throws SQLException {
        if (p_nEditMode != EditMode.ADDNEW) {
            return false;
        }

        if (fbByCode) {
            if (fsValue.equals((String) getMaster(fnRow, "sDeptIDxx"))) {
                return true;
            } else if (fsValue.equals((String) getMaster(fnRow, "xDeptName"))) {
                return true;
            }
        }

        String lsSQL = "SELECT"
                + "  sDeptIDxx"
                + ", sDeptName"
                + " FROM Department"
                + " WHERE cRecdStat = '1'";

        JSONObject loJSON;

        if (p_bWithUI) {
            loJSON = showFXDialog.jsonSearch(
                    p_oApp,
                    lsSQL,
                    fsValue,
                    "ID»Department",
                    "sDeptIDxx»sDeptName",
                    "sDeptIDxx»sDeptName",
                    fbByCode ? 0 : 1);

            if (loJSON != null) {
                System.out.println("sDeptIDxx = " + (String) loJSON.get("sDeptIDxx"));
                System.out.println("sDeptName = " + (String) loJSON.get("sDeptName"));
                p_oMaster.updateString("sDeptIDxx", (String) loJSON.get("sDeptIDxx"));
                p_oMaster.updateString("xDeptName", (String) loJSON.get("sDeptName"));
                p_oMaster.updateRow();

                //recreate detail and other tables
                createDetail();
                createDetailEmployee();
                createDetailAllocation();
                createDetailAllocationEmp();
                createDetailDeductionAlloc();
                createDetailDeductionAllocEmp();

                if (p_oListener != null) {
                    p_oListener.MasterRetreive(17, getMaster(fnRow, "xDeptName"));
                }

                return true;
            }

            p_oMaster.updateString("sDeptIDxx", "");
            p_oMaster.updateString("xDeptName", "");
            p_oMaster.updateRow();

            //recreate detail and other tables
            createDetail();
            createDetailEmployee();
            createDetailAllocation();
            createDetailAllocationEmp();
            createDetailDeductionAlloc();
            createDetailDeductionAllocEmp();

            if (p_oListener != null) {
                p_oListener.MasterRetreive(17, "");
            }

            return false;
        }

        if (fbByCode) {
            lsSQL = MiscUtil.addCondition(lsSQL, "sDeptIDxx = " + SQLUtil.toSQL(fsValue));
        } else {
            lsSQL = MiscUtil.addCondition(lsSQL, "sDeptName LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        lsSQL += " LIMIT 1";
        ResultSet loRS = p_oApp.executeQuery(lsSQL);

        JSONArray loArray = MiscUtil.RS2JSON(loRS);
        MiscUtil.close(loRS);

        if (loArray.isEmpty()) {
            return false;
        }

        loJSON = (JSONObject) loArray.get(0);

        p_oMaster.updateString("sDeptIDxx", (String) loJSON.get("sDeptIDxx"));
        p_oMaster.updateString("xDeptName", (String) loJSON.get("sDeptName"));
        p_oMaster.updateRow();

        if (p_oListener != null) {
            p_oListener.MasterRetreive(17, p_oMaster.getString("xDeptName"));
        }

        //recreate detail and other tables
        createDetail();
        createDetailEmployee();
        createDetailAllocation();
        createDetailAllocationEmp();
        createDetailDeductionAlloc();
        createDetailDeductionAllocEmp();

        return true;
    }

    public boolean searchBranch(String fsValue, boolean fbByCode) throws SQLException {

//        if (fbByCode)
//            if (fsValue.equals((String) getBranch("sBranchCd"))) return true;
//        else
//            if (fsValue.equals((String) getBranch("sBranchNm"))) return true;
        createBranch();
        String lsSQL = getSQ_Branch();
        if (fbByCode) {
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchCd = " + SQLUtil.toSQL(fsValue));
        } else {
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        JSONObject loJSON;

        if (p_bWithUI) {
            loJSON = showFXDialog.jsonSearch(
                    p_oApp,
                    lsSQL,
                    fsValue,
                    "Code»Branch Name",
                    "sBranchCd»sBranchNm",
                    "sBranchCd»sBranchNm",
                    fbByCode ? 0 : 1);

            if (loJSON != null) {
                return OpenBranch((String) loJSON.get("sBranchCd"));
            } else {
                p_sMessage = "No record selected.";
                return false;
            }
        }

        if (fbByCode) {
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchCd = " + SQLUtil.toSQL(fsValue));
        } else {
            lsSQL = MiscUtil.addCondition(lsSQL, "sBranchNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
            lsSQL += " LIMIT 1";
        }

        ResultSet loRS = p_oApp.executeQuery(lsSQL);

        if (!loRS.next()) {
            MiscUtil.close(loRS);
            p_sMessage = "No bracnh found for the givern criteria.";
            return false;
        }

        lsSQL = loRS.getString("sBranchCd");
        MiscUtil.close(loRS);

        return OpenBranch(lsSQL);
    }

    public String getMessage() {
        return p_sMessage;
    }

    private void computeEmpTotalIncentiveAmountMaster() throws SQLException {
        int lnCtr4;
        for (lnCtr4 = 1; lnCtr4 <= getItemMasterCount(); lnCtr4++) {

            p_oMaster.absolute(lnCtr4);

            String lsSQL;
            ResultSet loRS;
            RowSetFactory factory = RowSetProvider.newFactory();

            //open detail
            lsSQL = MiscUtil.addCondition(getSQ_MasterDetail(), "a.sTransNox = " + SQLUtil.toSQL(p_oMaster.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oDetail = factory.createCachedRowSet();
            p_oDetail.populate(loRS);
            MiscUtil.close(loRS);
            //open incentive
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Allocation(), "a.sTransNox = " + SQLUtil.toSQL(p_oMaster.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oAllctn = factory.createCachedRowSet();
            p_oAllctn.populate(loRS);
            MiscUtil.close(loRS);

            //open incentive employee allocation
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Allocation_Emp(), "a.sTransNox = " + SQLUtil.toSQL(p_oMaster.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oAllctn_Emp = factory.createCachedRowSet();
            p_oAllctn_Emp.populate(loRS);
            MiscUtil.close(loRS);

            //open deductions
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Deduction(), "sTransNox = " + SQLUtil.toSQL(p_oMaster.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oDedctn = factory.createCachedRowSet();
            p_oDedctn.populate(loRS);
            MiscUtil.close(loRS);

            //open deductions employee alloction
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Deduction_Emp(), "a.sTransNox = " + SQLUtil.toSQL(p_oMaster.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oDedctn_Emp = factory.createCachedRowSet();
            p_oDedctn_Emp.populate(loRS);
            MiscUtil.close(loRS);
            int lnCtr1, lnCtr2, lnCtr3;
            int lnDetRow = getItemCount();
            int lnIncRow;
            int lnAlcRow;
            double lnTotalAmt, lnPercentx, lnAllcAmtx, lnIncentve, lnDeductnx;
            double lnTotalInc, lnTotalDed;
            double transTotal = 0.0;

            System.out.println("sTransNox = " + p_oMaster.getString("sTransNox"));
            System.out.println();
            for (lnCtr1 = 1; lnCtr1 <= lnDetRow; lnCtr1++) {
                p_oDetail.absolute(lnCtr1);
                lnTotalInc = 0.00;
                lnTotalDed = 0.00;
                lnTotalAmt = 0.00;

                //incentive
                lnIncRow = getIncentiveCount();
                lnAlcRow = getIncentiveEmployeeAllocationCount();
                for (lnCtr2 = 1; lnCtr2 <= lnIncRow; lnCtr2++) {
                    p_oAllctn.absolute(lnCtr2);

                    if (p_oAllctn.getString("xByPercnt").equals("2")) {
                        lnAllcAmtx = getAllocatedIncentive(lnCtr2, "2");
                    } else {
                        lnAllcAmtx = 0.00;
                    }

                    for (lnCtr3 = 1; lnCtr3 <= lnAlcRow; lnCtr3++) {
                        p_oAllctn_Emp.absolute(lnCtr3);

                        lnIncentve = 0.00;
                        if (p_oAllctn.getString("sInctveCD").equals(p_oAllctn_Emp.getString("sInctveCD"))
                                && getDetail(lnCtr1, "sEmployID").equals(p_oAllctn_Emp.getString("sEmployID"))) {

                            switch (p_oAllctn.getString("xByPercnt")) {
                                case "0":
                                    lnIncentve = DecryptAmount(p_oAllctn_Emp.getString("nAllcAmtx"));

                                    lnTotalAmt += lnIncentve;
                                    lnTotalAmt = lnTotalAmt * 100 / 100; //round off
                                    break;
                                case "1":
                                    lnPercentx = p_oAllctn_Emp.getDouble("nAllcPerc") / 100;
                                    lnPercentx = lnPercentx * DecryptAmount(p_oAllctn.getString("nInctvAmt"));

                                    lnIncentve = lnPercentx * 100 / 100;

                                    lnTotalAmt += lnIncentve;
                                    lnTotalAmt = lnTotalAmt * 100 / 100; //round off
                                    break;
                                case "2":
                                    lnIncentve = DecryptAmount(p_oAllctn_Emp.getString("nAllcAmtx"));

                                    lnPercentx = p_oAllctn_Emp.getDouble("nAllcPerc") / 100;
                                    lnPercentx = lnPercentx * (DecryptAmount(p_oAllctn.getString("nInctvAmt")) - lnAllcAmtx);

                                    lnIncentve += lnPercentx * 100 / 100;

                                    lnTotalAmt += lnIncentve;
                                    lnTotalAmt = lnTotalAmt * 100 / 100; //round off
                                    break;
                            }

                            p_oAllctn_Emp.updateObject("nTotalAmt", lnIncentve);
                            p_oAllctn_Emp.updateRow();

                            lnTotalInc += lnIncentve;
                        }
                    }
                }

                //deductions
                lnIncRow = getDeductionCount();
                lnAlcRow = getDeductionEmployeeAllocationCount();
                for (lnCtr2 = 1; lnCtr2 <= lnIncRow; lnCtr2++) {
                    p_oDedctn.absolute(lnCtr2);

                    lnAllcAmtx = getAllocatedDeduction(lnCtr2, "2");

                    for (lnCtr3 = 1; lnCtr3 <= lnAlcRow; lnCtr3++) {
                        p_oDedctn_Emp.absolute(lnCtr3);

                        lnDeductnx = 0.00;
                        if (p_oDedctn.getInt("nEntryNox") == p_oDedctn_Emp.getInt("nEntryNox")
                                && p_oDetail.getString("sEmployID").equals(p_oDedctn_Emp.getString("sEmployID"))) {

                            lnDeductnx = DecryptAmount(p_oDedctn_Emp.getString("nAllcAmtx"));

                            lnPercentx = p_oDedctn_Emp.getDouble("nAllcPerc") / 100;
                            lnPercentx = lnPercentx * (DecryptAmount(p_oDedctn.getString("nDedctAmt")) - lnAllcAmtx);

                            lnDeductnx += lnPercentx * 100 / 100;

                            lnTotalAmt -= lnDeductnx;
                            lnTotalAmt = lnTotalAmt * 100 / 100; //round off

                            p_oDedctn_Emp.updateObject("nTotalAmt", lnDeductnx);
                            p_oDedctn_Emp.updateRow();

                            lnTotalDed += lnDeductnx;
                            break;
                        }
                    }
                }

                p_oDetail.updateDouble("xIncentve", lnTotalInc);
                p_oDetail.updateDouble("xDeductnx", lnTotalDed);
                p_oDetail.updateString("nTotalAmt", EncryptAmount(lnTotalInc - lnTotalDed));
                transTotal = transTotal + (lnTotalInc - lnTotalDed);

                System.out.println("xIncentve = " + lnTotalInc);
            }
            p_oMaster.updateString("xTotalAmt", String.valueOf(transTotal));
            p_oMaster.updateRow();
        }

    }

    private void computeEmpTotalIncentiveAmount() throws SQLException {
        int lnDetRow = getEmpCount();
        int lnIncRow;
        int lnAlcRow;

        int lnCtr1, lnCtr2, lnCtr3;
        double lnTotalAmt, lnPercentx, lnAllcAmtx, lnIncentve, lnDeductnx;
        double lnTotalInc, lnTotalDed;
        double transTotal = 0.0;
        createDetailEmployeeNew();

        for (lnCtr1 = 1; lnCtr1 <= lnDetRow; lnCtr1++) {
            p_oDetailEmp.absolute(lnCtr1);

            lnTotalInc = 0.00;
            lnTotalDed = 0.00;
            lnTotalAmt = 0.00;
            String lsSQL;
            String lsCondition;
            ResultSet loRS;
            RowSetFactory factory = RowSetProvider.newFactory();
            createDetailAllocation();
            createDetailAllocationEmp();
            createDetailDeductionAlloc();
            createDetailDeductionAllocEmp();

            //open incentive
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Allocation(), "a.sTransNox = " + SQLUtil.toSQL(p_oDetailEmp.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oAllctn = factory.createCachedRowSet();
            p_oAllctn.populate(loRS);
            MiscUtil.close(loRS);

            //open incentive employee allocation
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Allocation_Emp(), "a.sTransNox = " + SQLUtil.toSQL(p_oDetailEmp.getString("sTransNox")) + " AND a.sEmployID = " + SQLUtil.toSQL(p_oDetailEmp.getString("sEmployID")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oAllctn_Emp = factory.createCachedRowSet();
            p_oAllctn_Emp.populate(loRS);
            MiscUtil.close(loRS);

            //open deductions
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Deduction(), "sTransNox = " + SQLUtil.toSQL(p_oDetailEmp.getString("sTransNox")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oDedctn = factory.createCachedRowSet();
            p_oDedctn.populate(loRS);
            MiscUtil.close(loRS);

            //open deductions employee alloction
            lsSQL = MiscUtil.addCondition(getSQ_Detail_Deduction_Emp(), "a.sTransNox = " + SQLUtil.toSQL(p_oDetailEmp.getString("sTransNox")) + "AND a.sEmployID = " + SQLUtil.toSQL(p_oDetailEmp.getString("sEmployID")));
            loRS = p_oApp.executeQuery(lsSQL);
            p_oDedctn_Emp = factory.createCachedRowSet();
            p_oDedctn_Emp.populate(loRS);
            MiscUtil.close(loRS);
            //incentive
            lnIncRow = getIncentiveCount();
            lnAlcRow = getIncentiveEmployeeAllocationCount();

            for (lnCtr2 = 1; lnCtr2 <= lnIncRow; lnCtr2++) {
                p_oAllctn.absolute(lnCtr2);

                if (p_oAllctn.getString("xByPercnt").equals("2")) {
                    lnAllcAmtx = getAllocatedIncentive(lnCtr2, "2");
                } else {
                    lnAllcAmtx = 0.00;
                }

                for (lnCtr3 = 1; lnCtr3 <= lnAlcRow; lnCtr3++) {
                    p_oAllctn_Emp.absolute(lnCtr3);

                    lnIncentve = 0.00;
                    if (p_oAllctn.getString("sInctveCD").equals(p_oAllctn_Emp.getString("sInctveCD"))
                            && p_oDetailEmp.getString("sEmployID").equals(p_oAllctn_Emp.getString("sEmployID"))) {

                        switch (p_oAllctn.getString("xByPercnt")) {
                            case "0":
                                lnIncentve = DecryptAmount(p_oAllctn_Emp.getString("nAllcAmtx"));

                                lnTotalAmt += lnIncentve;
                                lnTotalAmt = lnTotalAmt * 100 / 100; //round off
                                break;
                            case "1":
                                lnPercentx = p_oAllctn_Emp.getDouble("nAllcPerc") / 100;
                                lnPercentx = lnPercentx * DecryptAmount(p_oAllctn.getString("nInctvAmt"));

                                lnIncentve = lnPercentx * 100 / 100;

                                lnTotalAmt += lnIncentve;
                                lnTotalAmt = lnTotalAmt * 100 / 100; //round off
                                break;
                            case "2":
                                lnIncentve = DecryptAmount(p_oAllctn_Emp.getString("nAllcAmtx"));

                                lnPercentx = p_oAllctn_Emp.getDouble("nAllcPerc") / 100;
                                lnPercentx = lnPercentx * (DecryptAmount(p_oAllctn.getString("nInctvAmt")) - lnAllcAmtx);

                                lnIncentve += lnPercentx * 100 / 100;

                                lnTotalAmt += lnIncentve;
                                lnTotalAmt = lnTotalAmt * 100 / 100; //round off
                                break;
                        }

                        p_oAllctn_Emp.updateObject("nTotalAmt", lnIncentve);
                        p_oAllctn_Emp.updateRow();

                        lnTotalInc += lnIncentve;
                    }
                }

                //check next record if same employee id
                //if same
                //go to next, lnTotalInc + sa next
                //else
                //go to next, reset lnTotalInc
                //get inc value
            }

            //deductions
            lnIncRow = getDeductionCount();
            lnAlcRow = getDeductionEmployeeAllocationCount();
            for (lnCtr2 = 1; lnCtr2 <= lnIncRow; lnCtr2++) {
                p_oDedctn.absolute(lnCtr2);

                lnAllcAmtx = getAllocatedDeduction(lnCtr2, "2");

                for (lnCtr3 = 1; lnCtr3 <= lnAlcRow; lnCtr3++) {
                    p_oDedctn_Emp.absolute(lnCtr3);

                    lnDeductnx = 0.00;
                    if (p_oDedctn.getInt("nEntryNox") == p_oDedctn_Emp.getInt("nEntryNox")
                            && p_oDetailEmp.getString("sEmployID").equals(p_oDedctn_Emp.getString("sEmployID"))) {

                        lnDeductnx = DecryptAmount(p_oDedctn_Emp.getString("nAllcAmtx"));

                        lnPercentx = p_oDedctn_Emp.getDouble("nAllcPerc") / 100;
                        lnPercentx = lnPercentx * (DecryptAmount(p_oDedctn.getString("nDedctAmt")) - lnAllcAmtx);

                        lnDeductnx += lnPercentx * 100 / 100;

                        lnTotalAmt -= lnDeductnx;
                        lnTotalAmt = lnTotalAmt * 100 / 100; //round off

                        p_oDedctn_Emp.updateObject("nTotalAmt", lnDeductnx);
                        p_oDedctn_Emp.updateRow();

                        lnTotalDed += lnDeductnx;
                        break;
                    }
                }
            }

            p_oDetailEmp.updateString("xIncentve", String.valueOf(lnTotalInc));
            p_oDetailEmp.updateString("xDeductnx", String.valueOf(lnTotalDed));
            p_oDetailEmp.updateString("nTotalAmt", EncryptAmount(lnTotalInc - lnTotalDed));
            transTotal = transTotal + (lnTotalInc - lnTotalDed);
            p_oDetailEmp.updateRow();

        }

    }

    private double getAllocatedIncentive(int fnRow, String fcByPercnt) throws SQLException {
        int lnCtr;
        int lnRow = getIncentiveEmployeeAllocationCount();
        double lnAllocated = 0.00;

        String lsInctveCD = (String) getIncentiveInfo(fnRow, "sInctveCD");

        switch (fcByPercnt) {
            case "0":
            case "2":
                for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
                    p_oAllctn_Emp.absolute(lnCtr);
                    if (lsInctveCD.equals(p_oAllctn_Emp.getString("sInctveCD"))) {
                        lnAllocated += DecryptAmount(p_oAllctn_Emp.getString("nAllcAmtx"));
                    }
                }
                break;
            case "1":
                for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
                    p_oAllctn_Emp.absolute(lnCtr);
                    if (lsInctveCD.equals(p_oAllctn_Emp.getString("sInctveCD"))) {
                        lnAllocated += p_oAllctn_Emp.getDouble("nAllcPerc");
                    }
                }
        }

        return lnAllocated;
    }

    private double getAllocatedDeduction(int fnRow, String fcByPercnt) throws SQLException {
        int lnCtr;
        int lnRow = getDeductionEmployeeAllocationCount();
        double lnAllocated = 0.00;

        switch (fcByPercnt) {
            case "0":
            case "2":
                for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
                    p_oDedctn_Emp.absolute(lnCtr);
                    if (fnRow == p_oDedctn_Emp.getInt("nEntryNox")) {
                        lnAllocated += DecryptAmount(p_oDedctn_Emp.getString("nAllcAmtx"));
                    }
                }
                break;
            case "1":
                for (lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
                    p_oDedctn_Emp.absolute(lnCtr);
                    if (fnRow == p_oDedctn_Emp.getInt("nEntryNox")) {
                        lnAllocated += p_oDedctn_Emp.getDouble("nAllcPerc");
                    }
                }
        }

        return lnAllocated;
    }

    private void createDetailDeductionAllocEmp() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(7);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "nEntryNox");
        meta.setColumnLabel(2, "nEntryNox");
        meta.setColumnType(2, Types.INTEGER);

        meta.setColumnName(3, "sEmployID");
        meta.setColumnLabel(3, "sEmployID");
        meta.setColumnType(3, Types.VARCHAR);
        meta.setColumnDisplaySize(3, 12);

        meta.setColumnName(4, "nAllcPerc");
        meta.setColumnLabel(4, "nAllcPerc");
        meta.setColumnType(4, Types.DOUBLE);

        meta.setColumnName(5, "nAllcAmtx");
        meta.setColumnLabel(5, "nAllcAmtx");
        meta.setColumnType(5, Types.VARCHAR);
        meta.setColumnDisplaySize(5, 32);

        meta.setColumnName(6, "xEmployNm");
        meta.setColumnLabel(6, "xEmployNm");
        meta.setColumnType(6, Types.VARCHAR);

        meta.setColumnName(7, "nTotalAmt");
        meta.setColumnLabel(7, "nTotalAmt");
        meta.setColumnType(7, Types.DOUBLE);

        p_oDedctn_Emp = new CachedRowSetImpl();
        p_oDedctn_Emp.setMetaData(meta);
    }

    private void createDetailDeductionAlloc() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(4);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "nEntryNox");
        meta.setColumnLabel(2, "nEntryNox");
        meta.setColumnType(2, Types.INTEGER);

        meta.setColumnName(3, "sRemarksx");
        meta.setColumnLabel(3, "sRemarksx");
        meta.setColumnType(3, Types.VARCHAR);
        meta.setColumnDisplaySize(3, 64);

        meta.setColumnName(4, "nDedctAmt");
        meta.setColumnLabel(4, "nDedctAmt");
        meta.setColumnType(4, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        p_oDedctn = new CachedRowSetImpl();
        p_oDedctn.setMetaData(meta);
    }

    private void createDetailAllocationEmp() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(8);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "sEmployID");
        meta.setColumnLabel(2, "sEmployID");
        meta.setColumnType(2, Types.VARCHAR);
        meta.setColumnDisplaySize(2, 12);

        meta.setColumnName(3, "sInctveCD");
        meta.setColumnLabel(3, "sInctveCD");
        meta.setColumnType(3, Types.VARCHAR);
        meta.setColumnDisplaySize(3, 3);

        meta.setColumnName(4, "nAllcPerc");
        meta.setColumnLabel(4, "nAllcPerc");
        meta.setColumnType(4, Types.DOUBLE);

        meta.setColumnName(5, "nAllcAmtx");
        meta.setColumnLabel(5, "nAllcAmtx");
        meta.setColumnType(5, Types.VARCHAR);
        meta.setColumnDisplaySize(5, 32);

        meta.setColumnName(6, "xEmployNm");
        meta.setColumnLabel(6, "xEmployNm");
        meta.setColumnType(6, Types.VARCHAR);

        meta.setColumnName(7, "xInctvNme");
        meta.setColumnLabel(7, "xInctvNme");
        meta.setColumnType(7, Types.VARCHAR);

        meta.setColumnName(8, "nTotalAmt");
        meta.setColumnLabel(8, "nTotalAmt");
        meta.setColumnType(8, Types.DOUBLE);

        p_oAllctn_Emp = new CachedRowSetImpl();
        p_oAllctn_Emp.setMetaData(meta);
    }

    private void createDetailAllocation() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(10);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "sInctveCD");
        meta.setColumnLabel(2, "sInctveCD");
        meta.setColumnType(2, Types.VARCHAR);
        meta.setColumnDisplaySize(2, 3);

        meta.setColumnName(3, "nQtyGoalx");
        meta.setColumnLabel(3, "nQtyGoalx");
        meta.setColumnType(3, Types.INTEGER);

        meta.setColumnName(4, "nQtyActlx");
        meta.setColumnLabel(4, "nQtyActlx");
        meta.setColumnType(4, Types.INTEGER);

        meta.setColumnName(5, "nAmtGoalx");
        meta.setColumnLabel(5, "nAmtGoalx");
        meta.setColumnType(5, Types.DOUBLE);

        meta.setColumnName(6, "nAmtActlx");
        meta.setColumnLabel(6, "nAmtActlx");
        meta.setColumnType(6, Types.DOUBLE);

        meta.setColumnName(7, "nInctvAmt");
        meta.setColumnLabel(7, "nInctvAmt");
        meta.setColumnType(7, Types.VARCHAR);
        meta.setColumnDisplaySize(7, 32);

        meta.setColumnName(8, "sRemarksx");
        meta.setColumnLabel(8, "sRemarksx");
        meta.setColumnType(8, Types.VARCHAR);
        meta.setColumnDisplaySize(8, 64);

        meta.setColumnName(9, "xInctvNme");
        meta.setColumnLabel(9, "xInctvNme");
        meta.setColumnType(9, Types.VARCHAR);

        meta.setColumnName(10, "xByPercnt");
        meta.setColumnLabel(10, "xByPercnt");
        meta.setColumnType(10, Types.VARCHAR);

        p_oAllctn = new CachedRowSetImpl();
        p_oAllctn.setMetaData(meta);
    }

    private void createDetail() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(12);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "nEntryNox");
        meta.setColumnLabel(2, "nEntryNox");
        meta.setColumnType(2, Types.INTEGER);

        meta.setColumnName(3, "sEmployID");
        meta.setColumnLabel(3, "sEmployID");
        meta.setColumnType(3, Types.VARCHAR);
        meta.setColumnDisplaySize(3, 12);

        meta.setColumnName(4, "nTotalAmt");
        meta.setColumnLabel(4, "nTotalAmt");
        meta.setColumnType(4, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(5, "xEmployNm");
        meta.setColumnLabel(5, "xEmployNm");
        meta.setColumnType(5, Types.VARCHAR);

        meta.setColumnName(6, "xEmpLevNm");
        meta.setColumnLabel(6, "xEmpLevNm");
        meta.setColumnType(6, Types.VARCHAR);

        meta.setColumnName(7, "xPositnNm");
        meta.setColumnLabel(7, "xPositnNm");
        meta.setColumnType(7, Types.VARCHAR);

        meta.setColumnName(8, "xSrvcYear");
        meta.setColumnLabel(8, "xSrvcYear");
        meta.setColumnType(8, Types.VARCHAR);

        meta.setColumnName(9, "xBranchNm");
        meta.setColumnLabel(9, "xBranchNm");
        meta.setColumnType(9, Types.VARCHAR);

        meta.setColumnName(10, "sMonthxxx");
        meta.setColumnLabel(10, "sMonthxxx");
        meta.setColumnType(10, Types.VARCHAR);

        meta.setColumnName(11, "sRemarksx");
        meta.setColumnLabel(11, "sRemarksx");
        meta.setColumnType(11, Types.VARCHAR);

        meta.setColumnName(12, "cTranStat");
        meta.setColumnLabel(12, "cTranStat");
        meta.setColumnType(12, Types.CHAR);
        meta.setColumnDisplaySize(1, 1);

        p_oDetail = new CachedRowSetImpl();
        p_oDetail.setMetaData(meta);
    }

    private void createDetailEmployee() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(12);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "sEmployID");
        meta.setColumnLabel(2, "sEmployID");
        meta.setColumnType(2, Types.VARCHAR);
        meta.setColumnDisplaySize(2, 12);

        meta.setColumnName(3, "xEmployNm");
        meta.setColumnLabel(3, "xEmployNm");
        meta.setColumnType(3, Types.VARCHAR);

        meta.setColumnName(4, "xDeductnx");
        meta.setColumnLabel(4, "xDeductnx");
        meta.setColumnType(4, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(5, "xIncentve");
        meta.setColumnLabel(5, "xIncentve");
        meta.setColumnType(5, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(6, "nTotalAmt");
        meta.setColumnLabel(6, "nTotalAmt");
        meta.setColumnType(6, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(7, "xPositnNm");
        meta.setColumnLabel(7, "xPositnNm");
        meta.setColumnType(7, Types.VARCHAR);

        meta.setColumnName(8, "xBranchNm");
        meta.setColumnLabel(8, "xBranchNm");
        meta.setColumnType(8, Types.VARCHAR);

        meta.setColumnName(9, "xBankName");
        meta.setColumnLabel(9, "xBankName");
        meta.setColumnType(9, Types.VARCHAR);

        meta.setColumnName(10, "xBankAcct");
        meta.setColumnLabel(10, "xBankAcct");
        meta.setColumnType(10, Types.VARCHAR);

        meta.setColumnName(11, "sMonthxxx");
        meta.setColumnLabel(11, "sMonthxxx");
        meta.setColumnType(11, Types.VARCHAR);

        p_oDetailEmp = new CachedRowSetImpl();
        p_oDetailEmp.setMetaData(meta);
    }

    private void createDetailEmployeeNew() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(12);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "sEmployID");
        meta.setColumnLabel(2, "sEmployID");
        meta.setColumnType(2, Types.VARCHAR);
        meta.setColumnDisplaySize(2, 12);

        meta.setColumnName(3, "xEmployNm");
        meta.setColumnLabel(3, "xEmployNm");
        meta.setColumnType(3, Types.VARCHAR);

        meta.setColumnName(4, "xDeductnx");
        meta.setColumnLabel(4, "xDeductnx");
        meta.setColumnType(4, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(5, "xIncentve");
        meta.setColumnLabel(5, "xIncentve");
        meta.setColumnType(5, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(6, "nTotalAmt");
        meta.setColumnLabel(6, "nTotalAmt");
        meta.setColumnType(6, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(7, "xPositnNm");
        meta.setColumnLabel(7, "xPositnNm");
        meta.setColumnType(7, Types.VARCHAR);

        meta.setColumnName(8, "xBranchNm");
        meta.setColumnLabel(8, "xBranchNm");
        meta.setColumnType(8, Types.VARCHAR);

        meta.setColumnName(9, "xBankName");
        meta.setColumnLabel(9, "xBankName");
        meta.setColumnType(9, Types.VARCHAR);

        meta.setColumnName(10, "xBankAcct");
        meta.setColumnLabel(10, "xBankAcct");
        meta.setColumnType(10, Types.VARCHAR);

        meta.setColumnName(11, "sMonthxxx");
        meta.setColumnLabel(11, "sMonthxxx");
        meta.setColumnType(11, Types.VARCHAR);

        p_oDetailVal = new CachedRowSetImpl();
        p_oDetailVal.setMetaData(meta);
    }

    private void createDetailMaster() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(12);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "nEntryNox");
        meta.setColumnLabel(2, "nEntryNox");
        meta.setColumnType(2, Types.INTEGER);

        meta.setColumnName(3, "sEmployID");
        meta.setColumnLabel(3, "sEmployID");
        meta.setColumnType(3, Types.VARCHAR);
        meta.setColumnDisplaySize(3, 12);

        meta.setColumnName(4, "nTotalAmt");
        meta.setColumnLabel(4, "nTotalAmt");
        meta.setColumnType(4, Types.VARCHAR);
        meta.setColumnDisplaySize(4, 32);

        meta.setColumnName(5, "xEmployNm");
        meta.setColumnLabel(5, "xEmployNm");
        meta.setColumnType(5, Types.VARCHAR);

        meta.setColumnName(6, "xEmpLevNm");
        meta.setColumnLabel(6, "xEmpLevNm");
        meta.setColumnType(6, Types.VARCHAR);

        meta.setColumnName(7, "xPositnNm");
        meta.setColumnLabel(7, "xPositnNm");
        meta.setColumnType(7, Types.VARCHAR);

        meta.setColumnName(8, "xSrvcYear");
        meta.setColumnLabel(8, "xSrvcYear");
        meta.setColumnType(8, Types.VARCHAR);

        meta.setColumnName(9, "xBranchNm");
        meta.setColumnLabel(9, "xBranchNm");
        meta.setColumnType(9, Types.VARCHAR);

        meta.setColumnName(10, "sMonthxxx");
        meta.setColumnLabel(10, "sMonthxxx");
        meta.setColumnType(10, Types.VARCHAR);

        meta.setColumnName(11, "sRemarksx");
        meta.setColumnLabel(11, "sRemarksx");
        meta.setColumnType(11, Types.VARCHAR);

        meta.setColumnName(12, "cTranStat");
        meta.setColumnLabel(12, "cTranStat");
        meta.setColumnType(12, Types.CHAR);
        meta.setColumnDisplaySize(1, 1);

        p_oDetail = new CachedRowSetImpl();
        p_oDetail.setMetaData(meta);
    }
//    private void createDetailCategory() throws SQLException{
//        RowSetMetaData meta = new RowSetMetaDataImpl();        
//
//        meta.setColumnCount(17);
//        
//        meta.setColumnName(1, "sTransNox");
//        meta.setColumnLabel(1, "sTransNox");
//        meta.setColumnType(1, Types.VARCHAR);
//        meta.setColumnDisplaySize(1, 12);
//        
//        meta.setColumnName(2, "sEmployID");
//        meta.setColumnLabel(2, "sEmployID");
//        meta.setColumnType(2, Types.VARCHAR);
//        meta.setColumnDisplaySize(2, 12);
//        
//        meta.setColumnName(3, "nTotalAmt");
//        meta.setColumnLabel(3, "nTotalAmt");
//        meta.setColumnType(3, Types.VARCHAR);
//        meta.setColumnDisplaySize(3, 32);
//        
//        meta.setColumnName(4, "xIncentve");
//        meta.setColumnLabel(4, "xIncentve");
//        meta.setColumnType(4, Types.VARCHAR);
//        meta.setColumnDisplaySize(4, 32);
//        
//        meta.setColumnName(5, "xInctvPrc");
//        meta.setColumnLabel(5, "xInctvPrc");
//        meta.setColumnType(5, Types.DOUBLE);
//
//        meta.setColumnName(6, "xDeductnx");
//        meta.setColumnLabel(6, "xDeductnx");
//        meta.setColumnType(6, Types.VARCHAR);
//        meta.setColumnDisplaySize(6, 32);
//
//        meta.setColumnName(7, "xDedctPrc");
//        meta.setColumnLabel(7, "xDedctPrc");
//        meta.setColumnType(7, Types.DOUBLE);
//        
//        meta.setColumnName(8, "sInctveDs");
//        meta.setColumnLabel(8, "sInctveDs");
//        meta.setColumnType(8, Types.VARCHAR);
//
//        meta.setColumnName(9, "xEmployNm");
//        meta.setColumnLabel(9, "xEmployNm");
//        meta.setColumnType(9, Types.VARCHAR);
//        
//        meta.setColumnName(10, "xPositnNm");
//        meta.setColumnLabel(10, "xPositnNm");
//        meta.setColumnType(10, Types.VARCHAR);
//        
//        meta.setColumnName(11, "xBranchNm");
//        meta.setColumnLabel(11, "xBranchNm");
//        meta.setColumnType(11, Types.VARCHAR);
//        
//        meta.setColumnName(12, "sMonthxxx");
//        meta.setColumnLabel(12, "sMonthxxx");
//        meta.setColumnType(12, Types.VARCHAR);
//        
//        meta.setColumnName(13, "sRemarksx");
//        meta.setColumnLabel(13, "sRemarksx");
//        meta.setColumnType(13, Types.VARCHAR);
//        
//        meta.setColumnName(14, "cTranStat");
//        meta.setColumnLabel(14, "cTranStat");
//        meta.setColumnType(14, Types.CHAR);
//        meta.setColumnDisplaySize(14, 1);
//        
//        meta.setColumnName(15, "xInctvAmt");
//        meta.setColumnLabel(15, "xInctvAmt");
//        meta.setColumnType(15, Types.VARCHAR);
//        meta.setColumnDisplaySize(15, 32);
//        
//        meta.setColumnName(16, "xDedctAmt");
//        meta.setColumnLabel(16, "xDedctAmt");
//        meta.setColumnType(16, Types.VARCHAR);
//        meta.setColumnDisplaySize(16, 32);
//        
//        meta.setColumnName(17, "xByPercnt");
//        meta.setColumnLabel(17, "xByPercnt");
//        meta.setColumnType(17, Types.VARCHAR);
//        
//        
//        p_oDetailCateg = new CachedRowSetImpl();
//        p_oDetailCateg.setMetaData(meta); 
//    }

    private void createDetailCategory() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(17);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "sEmployID");
        meta.setColumnLabel(2, "sEmployID");
        meta.setColumnType(2, Types.VARCHAR);
        meta.setColumnDisplaySize(2, 12);

        meta.setColumnName(3, "xBranchNm");
        meta.setColumnLabel(3, "xBranchNm");
        meta.setColumnType(3, Types.VARCHAR);

        meta.setColumnName(4, "xEmployNm");
        meta.setColumnLabel(4, "xEmployNm");
        meta.setColumnType(4, Types.VARCHAR);

        meta.setColumnName(5, "sInctveDs");
        meta.setColumnLabel(5, "sInctveDs");
        meta.setColumnType(5, Types.VARCHAR);

        meta.setColumnName(6, "xIncentve");
        meta.setColumnLabel(6, "xIncentve");
        meta.setColumnType(6, Types.VARCHAR);
        meta.setColumnDisplaySize(6, 32);

        meta.setColumnName(7, "xInctvPrc");
        meta.setColumnLabel(7, "xInctvPrc");
        meta.setColumnType(7, Types.DOUBLE);

        meta.setColumnName(8, "xInctvAmt");
        meta.setColumnLabel(8, "xInctvAmt");
        meta.setColumnDisplaySize(6, 32);

        meta.setColumnName(9, "xPositnNm");
        meta.setColumnLabel(9, "xPositnNm");
        meta.setColumnType(9, Types.VARCHAR);

        meta.setColumnName(10, "sMonthxxx");
        meta.setColumnLabel(10, "sMonthxxx");
        meta.setColumnType(10, Types.VARCHAR);

        meta.setColumnName(11, "xByPercnt");
        meta.setColumnLabel(11, "xByPercnt");
        meta.setColumnType(11, Types.VARCHAR);

        p_oDetailCateg = new CachedRowSetImpl();
        p_oDetailCateg.setMetaData(meta);
    }

    private void createBranch() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(3);

        meta.setColumnName(1, "sBranchCd");
        meta.setColumnLabel(1, "sBranchCd");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 4);

        meta.setColumnName(2, "sBranchNm");
        meta.setColumnLabel(2, "sBranchNm");
        meta.setColumnType(2, Types.VARCHAR);

        meta.setColumnName(3, "sPeriodxx");
        meta.setColumnLabel(3, "sPeriodxx");
        meta.setColumnType(3, Types.VARCHAR);

        p_oBranch = new CachedRowSetImpl();
        p_oBranch.setMetaData(meta);
    }

    private void createEmployee() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(3);

        meta.setColumnName(1, "sEmployID");
        meta.setColumnLabel(1, "sEmployID");
        meta.setColumnType(1, Types.VARCHAR);

        meta.setColumnName(2, "sEmployID");
        meta.setColumnLabel(2, "sBranchNm");
        meta.setColumnType(2, Types.VARCHAR);

        meta.setColumnName(3, "sPeriodxx");
        meta.setColumnLabel(3, "sPeriodxx");
        meta.setColumnType(3, Types.VARCHAR);

        p_oBranch = new CachedRowSetImpl();
        p_oBranch.setMetaData(meta);
    }

    private void createMaster() throws SQLException {
        RowSetMetaData meta = new RowSetMetaDataImpl();

        meta.setColumnCount(7);

        meta.setColumnName(1, "sTransNox");
        meta.setColumnLabel(1, "sTransNox");
        meta.setColumnType(1, Types.VARCHAR);
        meta.setColumnDisplaySize(1, 12);

        meta.setColumnName(2, "sMonthxxx");
        meta.setColumnLabel(2, "sMonthxxx");
        meta.setColumnType(2, Types.VARCHAR);
        meta.setColumnDisplaySize(2, 6);

        meta.setColumnName(3, "sRemarksx");
        meta.setColumnLabel(3, "sRemarksx");
        meta.setColumnType(3, Types.VARCHAR);
        meta.setColumnDisplaySize(3, 128);

        meta.setColumnName(4, "xBranchNm");
        meta.setColumnLabel(4, "xBranchNm");
        meta.setColumnType(4, Types.VARCHAR);

        meta.setColumnName(5, "xDeptName");
        meta.setColumnLabel(5, "xDeptName");
        meta.setColumnType(5, Types.VARCHAR);

        meta.setColumnName(6, "cTranStat");
        meta.setColumnLabel(6, "cTranStat");
        meta.setColumnType(6, Types.VARCHAR);
        meta.setColumnDisplaySize(6, 1);

        meta.setColumnName(7, "xTotalAmt");
        meta.setColumnLabel(7, "xTotalAmt");
        meta.setColumnType(7, Types.DOUBLE);

        p_oMaster = new CachedRowSetImpl();
        p_oMaster.setMetaData(meta);

    }

    private double DecryptAmount(String fsValue) {
        return Double.valueOf(MySQLAESCrypt.Decrypt(fsValue, p_oApp.SIGNATURE));
    }

    private String EncryptAmount(double fnValue) {
        return MySQLAESCrypt.Encrypt(String.valueOf(fnValue), p_oApp.SIGNATURE);
    }

    private boolean isEntryOK() throws SQLException {
        if (System.getProperty(DEBUG_MODE).equals("0")) {
            if (Integer.valueOf(p_oApp.getEmployeeLevel()) < 1) {
                p_sMessage = "Your employee level is not authorized to use this transaction.";
                return false;
            }

            if (p_oApp.getUserLevel() < UserRight.SUPERVISOR) {
                p_sMessage = "Your account level is not authorized to use this transaction.";
                return false;
            }
        }

        //validate master
        p_oMaster.first();
        if (p_oMaster.getString("sMonthxxx").isEmpty()) {
            p_sMessage = "Period must not be empty.";
            return false;
        }

        //validate detail
        if (getItemCount() == 0) {
            p_sMessage = "No employee detected.";
            return false;
        }

        p_oDetail.beforeFirst();
        while (p_oDetail.next()) {
            if (DecryptAmount(p_oDetail.getString("nTotalAmt")) < 0.00) {
                p_sMessage = p_oDetail.getString("xEmployNm") + " has negative incentive total amount.";
                return false;
            }
        }

        //validate incentive
        if (getIncentiveCount() == 0) {
            p_sMessage = "No incentive added.";
            return false;
        }

        p_oAllctn.beforeFirst();
        while (p_oAllctn.next()) {
            if (DecryptAmount(p_oAllctn.getString("nInctvAmt")) <= 0.00) {
                p_sMessage = "Invalid incentive amount for " + p_oAllctn.getString("xInctvNme");
                return false;
            }
        }

        //validate employee incentive allocation
        if (getIncentiveEmployeeAllocationCount() == 0) {
            p_sMessage = "No incentive allocation for employees.";
            return false;
        }

        p_oAllctn_Emp.beforeFirst();
        while (p_oAllctn_Emp.next()) {
            if (p_oAllctn_Emp.getDouble("nAllcPerc") < 0.00) {
                p_sMessage = p_oAllctn_Emp.getString("xEmployNm") + " has negative incentive percentage allocation.";
                return false;
            }

            if (DecryptAmount(p_oAllctn_Emp.getString("nAllcAmtx")) < 0.00) {
                p_sMessage = p_oAllctn_Emp.getString("xEmployNm") + " has negative incentive amount allocation.";
                return false;
            }
        }

        //validate deductions
        if (getDeductionCount() > 0) {
            //validate employee incentive allocation
            if (getDeductionEmployeeAllocationCount() == 0) {
                p_sMessage = "No deduction allocation for employees.";
                return false;
            }

            p_oDedctn_Emp.beforeFirst();
            while (p_oDedctn_Emp.next()) {
                if (p_oDedctn_Emp.getDouble("nAllcPerc") < 0.00) {
                    p_sMessage = p_oDedctn_Emp.getString("xEmployNm") + " has negative deduction percentage allocation.";
                    return false;
                }

                if (DecryptAmount(p_oDedctn_Emp.getString("nAllcAmtx")) < 0.00) {
                    p_sMessage = p_oDedctn_Emp.getString("xEmployNm") + " has negative deduction amount allocation.";
                    return false;
                }
            }
        }

        return true;
    }

    public String getSQ_Branch() {
        String lsSQL = "";
        String lsStat = String.valueOf(p_nTranStat);

        lsSQL = "SELECT"
                + "  sBranchCd"
                + ", sBranchNm"
                + ", '' sPeriodxx"
                + " FROM Branch a"
                + " WHERE cRecdStat = 1";

        return lsSQL;
    }

    public String getSQ_Master() {
        String lsSQL = "";
        String lsStat = String.valueOf(p_nTranStat);

        if (lsStat.length() > 1) {
            for (int lnCtr = 0; lnCtr <= lsStat.length() - 1; lnCtr++) {
                lsSQL += ", " + SQLUtil.toSQL(Character.toString(lsStat.charAt(lnCtr)));
            }

            lsSQL = " a.cTranStat IN (" + lsSQL.substring(2) + ")";
        } else {
            lsSQL = " a.cTranStat = " + SQLUtil.toSQL(lsStat);
        }

        lsSQL = "SELECT"
                + "  a.sTransNox"
                + ", a.sMonthxxx"
                + ", a.sRemarksx"
                + ", a.cTranStat"
                + ", c.sBranchNm xBranchNm"
                + ", IFNULL(b.sDeptName, '') xDeptName"
                + ", 0.0 xTotalAmt"
                + " FROM Incentive_Master a"
                + " LEFT JOIN Department b ON a.sDeptIDxx = b.sDeptIDxx"
                + ", Branch c "
                + " WHERE " + lsSQL;

        return lsSQL;
    }

    private String getSQ_Transaction_Total() {
        return "SELECT"
                + " SUM(nTotalAmt) AS nTotalAmt"
                + " FROM Incentive_Detail";
    }

    private String getSQ_Detail() {
        String lsSQL = "";
        String lsStat = String.valueOf(p_nTranStat);

        if (lsStat.length() > 1) {
            for (int lnCtr = 0; lnCtr <= lsStat.length() - 1; lnCtr++) {
                lsSQL += ", " + SQLUtil.toSQL(Character.toString(lsStat.charAt(lnCtr)));
            }

            lsSQL = " a.cTranStat IN (" + lsSQL.substring(2) + ")";
        } else {
            lsSQL = " a.cTranStat = " + SQLUtil.toSQL(lsStat);
        }
        lsSQL = "SELECT "
                + "  IFNULL(a. sTransNox,'') sTransNox"
                + "  ,IFNULL(b.sEmployID,'') sEmployID"
                + "  ,IFNULL(d.sCompnyNm,'') xEmployNm"
                + "  ,0.0 xIncentve"
                + "  ,0.0 xDeductnx"
                + "  ,IFNULL(b.nTotalAmt,'') nTotalAmt"
                + "  ,IFNULL(e. sPositnNm, '') xPositnNm"
                + "  ,IFNULL(f.sBranchNm,'') xBranchNm"
                + "  ,IFNULL(h.sBankName,'') xBankName"
                + "  ,IFNULL(g.sBankAcct,'') xBankAcct"
                + "  ,IFNULL(a.sMonthxxx,'') sMonthxxx "
                + "FROM Incentive_Master a"
                + "  LEFT JOIN Incentive_Detail b"
                + "    ON a.sTransNox = b.sTransNox"
                + "  LEFT JOIN Employee_Master001 c"
                + "    ON b.sEmployID = c.sEmployID "
                + "  LEFT JOIN Client_Master d"
                + "    ON c.sEmployID   = d.sClientID "
                + "  LEFT JOIN `Position` e"
                + "    ON c.sPositnID   = e.sPositnID "
                + "  LEFT JOIN Branch f"
                + "    ON c.sBranchCd = f.sBranchCd "
                + "  LEFT JOIN Employee_Incentive_Bank_Info g"
                + "    ON b.sEmployID = g.sEmployID   "
                + "  LEFT JOIN Banks h"
                + "    ON g.sBankIDxx = h.sBankIDxx  "
                + "WHERE  " + lsSQL;
        return lsSQL;
    }
//    private String getSQ_Detail(){
//        String lsSQL = "";
//        String lsStat = String.valueOf(p_nTranStat);
//        
//        if (lsStat.length() > 1){
//            for (int lnCtr = 0; lnCtr <= lsStat.length()-1; lnCtr++){
//                lsSQL += ", " + SQLUtil.toSQL(Character.toString(lsStat.charAt(lnCtr)));
//            }
//            
//            lsSQL = " g.cTranStat IN (" + lsSQL.substring(2) + ")";
//        } else{            
//            lsSQL = " g.cTranStat = " + SQLUtil.toSQL(lsStat);
//        }
//        lsSQL = "SELECT " +
//                "  IFNULL(a.sTransNox, '')    sTransNox, " +
//                "  IFNULL(a.nEntryNox, '')    nEntryNox, " +
//                "  IFNULL(a.sEmployID, '')    sEmployID, " +
//                "  IFNULL(a.nTotalAmt, 0.0)    nTotalAmt, " +
//                "  IFNULL(c.sCompnyNm, '')    xEmployNm, " +
//                "  IFNULL(d.sEmpLevNm, '')    xEmpLevNm, " +
//                "  IFNULL(e.sPositnNm, '')    xPositnNm, " +
//                "  IFNULL(ROUND(DATEDIFF(NOW(), IFNULL(b.dStartEmp, b.dHiredxxx)) / 365), '')    xSrvcYear, " +
//                "  f.sBranchNm xBranchNm, " +
//                "  g.sMonthxxx, " +
//                "  g.sRemarksx, " +
//                "  g.cTranStat, " +
//                "  g.cTranStat, " +
//                "  IFNULL(h.sBankAcct,'') xBankAcct, " +
//                "  IFNULL(i.sBankName,'') xBankName, " +
//                "  0.00 xIncentve," +
//                "  0.00 xDeductnx " +
//                "FROM Incentive_Detail a, " +
//                "  Employee_Master001 b " +
//                "  LEFT JOIN Client_Master c " +
//                "    ON b.sEmployID = c.sClientID " +
//                "  LEFT JOIN Employee_Level d " +
//                "    ON b.sEmpLevID = d.sEmpLevID " +
//                "  LEFT JOIN `Position` e " +
//                "    ON b.sPositnID = e.sPositnID, " +
//                "  Branch f, " +
//                "  Incentive_Master g, " +
//                "  Employee_Incentive_Bank_Info h," +
//                "  Banks i " +
//                "WHERE a.sEmployID = b.sEmployID " +
//                "    AND " + lsSQL +
//                "    AND LEFT(a.sTransNox, 4) = f.sBranchCd " +
//                "    AND a.sTransNox = g.sTransNox " +
//                "    AND a.sEmployID = h.sEmployID "+
//                "    AND h.sBankIDxx = i.sBankIDxx ";
//        return lsSQL;
//    }
//   

    private String getSQ_EmployeeDetail() {
        String lsSQL = "";
        String lsStat = String.valueOf(p_nTranStat);

        if (lsStat.length() > 1) {
            for (int lnCtr = 0; lnCtr <= lsStat.length() - 1; lnCtr++) {
                lsSQL += ", " + SQLUtil.toSQL(Character.toString(lsStat.charAt(lnCtr)));
            }

            lsSQL = " a.cTranStat IN (" + lsSQL.substring(2) + ")";
        } else {
            lsSQL = " a.cTranStat = " + SQLUtil.toSQL(lsStat);
        }
        lsSQL = "SELECT b.sTransNox, "
                + "  b.sEmployID, "
                + "  IFNULL(i.sBranchNm,'') xBranchNm, "
                + "  IFNULL(g.sCompnyNm,'') xEmployNm, "
                + "  IFNULL(e.sInctveDs,'') sInctveDs,  "
                + "  IFNULL(c.nAllcAmtx,'') xIncentve, "
                + "  IFNULL(c.nAllcPerc,'') xInctvPrc, "
                + "  IFNULL(d.nInctvAmt,'') xInctvAmt, "
                + "  IFNULL(h.sPositnNm,'') xPositnNm,   "
                + "  IFNULL(a.sMonthxxx,'') sMonthxxx, "
                + "  e.cByPercnt xByPercnt "
                + "FROM Incentive_Master a "
                + "  LEFT JOIN Incentive_Detail b "
                + "    ON a.sTransNox = b.sTransNox "
                + "  LEFT JOIN Incentive_Detail_Allocation_Employee c "
                + "    ON b.sTransNox = c.sTransNox AND b.sEmployID   = c.sEmployID  "
                + "  LEFT JOIN Incentive_Detail_Allocation d "
                + "    ON c.sTransNox = d.sTransNox AND c.sInctveCD = d.sInctveCD   "
                + "  LEFT JOIN Incentive e "
                + "    ON c.sInctveCD = e.sInctveCD "
                + "  LEFT JOIN Employee_Master001 f "
                + "    ON b.sEmployID = f.sEmployID  "
                + "  LEFT JOIN Client_Master g "
                + "    ON f.sEmployID   = g.sClientID  "
                + "  LEFT JOIN `Position` h "
                + "    ON f.sPositnID   = h.sPositnID  "
                + "  LEFT JOIN Branch i "
                + "    ON f.sBranchCd = i.sBranchCd  "
                + "WHERE " + lsSQL;
        return lsSQL;
    }

    private String getSQ_MasterDetail() {
        return "SELECT"
                + "  a.sTransNox"
                + ", a.nEntryNox"
                + ", a.sEmployID"
                + ", a.nTotalAmt"
                + ", IFNULL(c.sCompnyNm, '') xEmployNm"
                + ", IFNULL(d.sEmpLevNm, '') xEmpLevNm"
                + ", IFNULL(e.sPositnNm, '') xPositnNm"
                + ", IFNULL(ROUND(DATEDIFF(NOW(), IFNULL(b.dStartEmp, b.dHiredxxx)) / 365), '') xSrvcYear"
                + ", 0.00 xIncentve"
                + ", 0.00 xDeductnx"
                + " FROM Incentive_Detail a"
                + ", Employee_Master001 b"
                + " LEFT JOIN Client_Master c ON b.sEmployID = c.sClientID"
                + " LEFT JOIN Employee_Level d ON b.sEmpLevID = d.sEmpLevID"
                + " LEFT JOIN `Position` e ON b.sPositnID = e.sPositnID"
                + " WHERE a.sEmployID = b.sEmployID"
                + " ORDER BY nEntryNox";
    }

    private String getSQ_Detail_Allocation() {
        return "SELECT"
                + "  a.sTransNox"
                + ", a.sInctveCD"
                + ", a.nQtyGoalx"
                + ", a.nQtyActlx"
                + ", a.nAmtGoalx"
                + ", a.nAmtActlx"
                + ", a.nInctvAmt"
                + ", a.sRemarksx"
                + ", IFNULL(b.sInctveDs, '') xInctvNme"
                + ", IFNULL(b.cByPercnt, '') xByPercnt"
                + " FROM Incentive_Detail_Allocation a"
                + ", Incentive b"
                + " WHERE a.sInctveCD = b.sInctveCD";
    }

    private String getSQ_Detail_Allocation_Emp() {
        return "SELECT"
                + "  a.sTransNox"
                + ", a.sEmployID"
                + ", a.sInctveCD"
                + ", a.nAllcPerc"
                + ", a.nAllcAmtx"
                + ", IFNULL(c.sInctveDs, '') xInctvNme"
                + ", IFNULL(d.sCompnyNm, '') xEmployNm"
                + ", IFNULL(e.sEmpLevNm, '') xEmpLevNm"
                + ", IFNULL(f.sPositnNm, '') xPositnNm"
                + ", IFNULL(ROUND(DATEDIFF(NOW(), IFNULL(b.dStartEmp, b.dHiredxxx)) / 365), '') xSrvcYear"
                + ", 0.00 nTotalAmt"
                + " FROM Incentive_Detail_Allocation_Employee a"
                + " LEFT JOIN Incentive c ON a.sInctveCD = c.sInctveCD"
                + ", Employee_Master001 b"
                + " LEFT JOIN Client_Master d ON b.sEmployID = d.sClientID"
                + " LEFT JOIN Employee_Level e ON b.sEmpLevID = e.sEmpLevID"
                + " LEFT JOIN `Position` f ON b.sPositnID = f.sPositnID"
                + " WHERE a.sEmployID = b.sEmployID"
                + " ORDER BY e.sEmpLevID DESC, IFNULL(b.dStartEmp, b.dHiredxxx), d.sCompnyNm";
    }

    private String getSQ_Detail_Deduction() {
        return "SELECT"
                + "  sTransNox"
                + ", nEntryNox"
                + ", sRemarksx"
                + ", nDedctAmt"
                + " FROM Incentive_Detail_Ded_Allocation";
    }

    private String getSQ_Detail_Deduction_Emp() {
        return "SELECT"
                + "  a.sTransNox"
                + ", a.nEntryNox"
                + ", a.sEmployID"
                + ", a.nAllcPerc"
                + ", a.nAllcAmtx"
                + ", IFNULL(b.sCompnyNm, '') xEmployNm"
                + ", 0.00 nTotalAmt"
                + " FROM Incentive_Detail_Ded_Allocation_Employee a"
                + " LEFT JOIN Client_Master b ON a.sEmployID = b.sClientID";
    }

    private String getSQ_Record() {
        String lsSQL = "";
        lsSQL = "SELECT"
                + "  a.sEmployID"
                + ", b.sCompnyNm"
                + ", c.sBranchNm"
                + ", a.sBranchCd"
                + " FROM Employee_Master001 a"
                + ", Client_Master b"
                + ", Branch c"
                + " WHERE a.sEmployID = b.sClientID"
                + " AND a.sBranchCd = c.sBranchCd"
                + " AND a.cRecdStat = '1'"
                + " AND ISNULL(a.dFiredxxx)";

        return lsSQL;
    }

    public boolean searchEmployee(String fsValue, boolean fbByCode) throws SQLException {

        String lsSQL = "SELECT"
                + "  a.sEmployID"
                + ", b.sCompnyNm"
                + ", c.sBranchNm"
                + ", c.sBranchCD"
                + " FROM Employee_Master001 a"
                + ", Client_Master b"
                + ", Branch c"
                + " WHERE a.sEmployID = b.sClientID"
                + " AND a.sBranchCd = c.sBranchCd"
                + " AND a.cRecdStat = '1'"
                + " AND ISNULL(a.dFiredxxx)";

        ResultSet loRS;
        JSONObject loJSON;

        if (p_bWithUI) {
            loJSON = showFXDialog.jsonSearch(
                    p_oApp,
                    lsSQL,
                    fsValue,
                    "ID»Employee»Branch",
                    "a.sEmployID»b.sCompnyNm»c.sBranchNm",
                    "a.sEmployID»b.sCompnyNm»c.sBranchNm",
                    fbByCode ? 0 : 1);

            if (loJSON != null) {

                lsSQL = MiscUtil.addCondition(getSQ_Record(), "a.sEmployID = " + SQLUtil.toSQL((String) loJSON.get("sEmployID")));
                loRS = p_oApp.executeQuery(lsSQL);

                return OpenRecord((String) loJSON.get("sEmployID"))
                        && OpenBranch((String) loJSON.get("sBranchCD"));
            } else {
                return false;
            }

        }

        if (fbByCode) {
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sEmployID = " + SQLUtil.toSQL(fsValue));
        } else {
            lsSQL = MiscUtil.addCondition(lsSQL, "b.sCompnyNm LIKE " + SQLUtil.toSQL(fsValue + "%"));
        }

        lsSQL += " LIMIT 1";
        loRS = p_oApp.executeQuery(lsSQL);

        JSONArray loArray = MiscUtil.RS2JSON(loRS);
        MiscUtil.close(loRS);

        if (loArray.isEmpty()) {
            return false;
        }

        loJSON = (JSONObject) loArray.get(0);

        if (loJSON != null) {
            lsSQL = MiscUtil.addCondition(getSQ_Record(), "a.sEmployID = " + SQLUtil.toSQL((String) loJSON.get("sEmployID")));
            loRS = p_oApp.executeQuery(lsSQL);
            return OpenRecord((String) loJSON.get("sEmployID"));
        } else {
            return false;
        }
    }

    public boolean OpenRecord(String fsValue) throws SQLException {
        p_nEditMode = EditMode.UNKNOWN;

        if (p_oApp == null) {
            p_sMessage = "Application driver is not set.";
            return false;
        }

        p_sMessage = "";

        String lsSQL;
        ResultSet loRS;
        RowSetFactory factory = RowSetProvider.newFactory();

        //open master
        lsSQL = MiscUtil.addCondition(getSQ_Record(), "a.sEmployID = " + SQLUtil.toSQL(fsValue));
        loRS = p_oApp.executeQuery(lsSQL);
        p_oEmployee = factory.createCachedRowSet();
        p_oEmployee.populate(loRS);
        MiscUtil.close(loRS);

        if (p_oEmployee.size() == 0) {
            return false;
        }
        p_nEditMode = EditMode.READY;
        return true;
    }

    private int getColumnIndex(CachedRowSet loRS, String fsValue) throws SQLException {
        int lnIndex = 0;
        int lnRow = loRS.getMetaData().getColumnCount();

        for (int lnCtr = 1; lnCtr <= lnRow; lnCtr++) {
            if (fsValue.equals(loRS.getMetaData().getColumnLabel(lnCtr))) {
                lnIndex = lnCtr;
                break;
            }
        }

        return lnIndex;
    }

    private void loadConfig() {
        //update the value on configuration before deployment
        System.setProperty(DEBUG_MODE, "0");
        System.setProperty(REQUIRE_CSS, "0");
        System.setProperty(REQUIRE_CM, "1");
        System.setProperty(REQUIRE_BANK_ON_APPROVAL, "0");
    }

    private void computeEmpTotalIncentivesAmount() throws SQLException {

        double lnPercentx, lnIncentve, lnDeductnx;
        double lnInctvAmt;
        int lnCtr;
        for (lnCtr = 1; lnCtr <= getCategoryCount(); lnCtr++) {
            p_oDetailCateg.absolute(lnCtr);
            lnIncentve = 0.0;
            lnPercentx = 0.0;
            lnInctvAmt = 0.00;
            if (p_oDetailCateg.getDouble("xInctvPrc") > 0.00) {
                lnInctvAmt = DecryptAmount(p_oDetailCateg.getString("xInctvAmt"));
                lnInctvAmt = lnInctvAmt * p_oDetailCateg.getDouble("xInctvPrc") / 100;

            } else {
                lnInctvAmt = DecryptAmount(p_oDetailCateg.getString("xIncentve"));
                System.out.println(p_oDetailCateg.getString("xEmployNm") + " = " + p_oDetailCateg.getString("xIncentve"));
            }

            p_oDetailCateg.updateString("xIncentve", EncryptAmount(lnInctvAmt));
//            p_oDetailCateg.updateString("xDeductnx", EncryptAmount(lnDeductnx));
//            p_oDetailCateg.updateString("nTotalAmt", EncryptAmount(lnInctvAmt));
            p_oDetailCateg.updateRow();

        }
    }

}
