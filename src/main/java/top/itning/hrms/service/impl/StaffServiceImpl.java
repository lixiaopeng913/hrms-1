package top.itning.hrms.service.impl;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import top.itning.hrms.dao.StaffDao;
import top.itning.hrms.dao.WageDao;
import top.itning.hrms.dao.department.DepartmentDao;
import top.itning.hrms.dao.department.GrassrootDao;
import top.itning.hrms.dao.job.JobLevelDao;
import top.itning.hrms.dao.job.JobTitleDao;
import top.itning.hrms.dao.post.PositionCategoryDao;
import top.itning.hrms.dao.post.PositionTitleDao;
import top.itning.hrms.entity.Staff;
import top.itning.hrms.entity.department.Department;
import top.itning.hrms.entity.department.Grassroot;
import top.itning.hrms.entity.search.SearchStaff;
import top.itning.hrms.exception.defaults.IllegalParametersException;
import top.itning.hrms.exception.defaults.NoSuchIdException;
import top.itning.hrms.exception.defaults.NullParameterException;
import top.itning.hrms.service.StaffService;
import top.itning.hrms.util.StaffUtils;

import javax.persistence.criteria.Predicate;
import javax.servlet.ServletOutputStream;
import javax.transaction.Transactional;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.DataFormatException;

/**
 * 职工信息服务实线类
 *
 * @author Ning
 */
@Service
@Transactional(rollbackOn = Exception.class)
public class StaffServiceImpl implements StaffService {
    private static final Logger logger = LoggerFactory.getLogger(StaffServiceImpl.class);
    /**
     * 身份证号位数
     */
    private static final int ID_NUM_LENGTH = 18;

    @Autowired
    private StaffDao staffDao;

    @Autowired
    private DepartmentDao departmentDao;

    @Autowired
    private WageDao wageDao;

    @Autowired
    private GrassrootDao grassrootDao;

    @Autowired
    private PositionTitleDao positionTitleDao;

    @Autowired
    private PositionCategoryDao positionCategoryDao;

    @Autowired
    private JobTitleDao jobTitleDao;

    @Autowired
    private JobLevelDao jobLevelDao;

    @Override
    @Cacheable(cacheNames = "StaffInfoList", key = "#id")
    public List<Staff> getStaffInfoListByDepartmentID(String id) throws NullParameterException, NoSuchIdException {
        if (StringUtils.isEmpty(id)) {
            logger.warn("getStaffInfoListByDepartmentID::参数ID为空");
            throw new NullParameterException("参数ID为空");
        } else if (!departmentDao.exists(id)) {
            logger.warn("getStaffInfoListByDepartmentID::ID->" + id + "不存在");
            throw new NoSuchIdException("ID->" + id + "不存在");
        } else {
            logger.info("getStaffInfoListByDepartmentID::查找并返回职工集合");
            return staffDao.findByDepartment(departmentDao.findOne(id));
        }
    }

    @Override
    @CacheEvict(cacheNames = "StaffInfoList", key = "#staff.department.id")
    @CachePut(cacheNames = "StaffInfoByID", key = "#staff.id")
    public Staff addOrModifyStaffInfo(Staff staff) throws NumberFormatException, NullParameterException, DataFormatException {
        String nid = staff.getNid();
        //判断是否为数字
        if (!StringUtils.isNumeric(nid.substring(0, nid.length() - 1))) {
            logger.warn("addOrModifyStaffInfo::nid不是纯数字->" + nid);
            throw new NumberFormatException("身份证号码不是纯数字,请检查!");
        }
        //判断身份证号长度
        if (nid.length() != ID_NUM_LENGTH) {
            logger.warn("addOrModifyStaffInfo::nid位数为" + nid.length() + "与" + ID_NUM_LENGTH + "不匹配");
            throw new NumberFormatException("您输入的身份证号码位数为" + nid.length() + "位,不是" + ID_NUM_LENGTH + "位,请检查");
        }
        try {
            //根据身份证号设置出生日期
            staff.setBirthday(new SimpleDateFormat("yyyyMMdd").parse(nid.substring(6, 14)));
        } catch (ParseException e) {
            logger.warn("addOrModifyStaffInfo::出生日期格式化出错,日期->" + nid.substring(6, 14) + "异常信息->" + e.getMessage());
            throw new DataFormatException("出生日期格式化出错,检查日期是否有误");
        }
        //根据身份证号设置性别
        staff.setSex(Integer.parseInt(nid.substring(16, 17)) % 2 != 0);
        //根据身份证号设置年龄
        staff.setAge(String.valueOf(Calendar.getInstance().get(Calendar.YEAR) - Integer.parseInt(nid.substring(6, 10))));
        //检查必填字段为空
        if (StringUtils.isAnyEmpty(staff.getName(), staff.getBankID(), staff.getEmail(), staff.getNid(), staff.getAge(), staff.getAddress(), staff.getNaddress(), staff.getTel()) && !ObjectUtils.allNotNull(staff.getEthnic(), staff.getPs(), staff.getDepartment(), staff.getGrassroot(), staff.getPositionTitle(), staff.getPositionCategory(), staff.getBirthday())) {
            logger.warn("addOrModifyStaffInfo::必填参数为空");
            throw new NullParameterException("必填参数为空");
        }
        logger.debug("addOrModifyStaffInfo::职工信息->" + staff);
        return staffDao.saveAndFlush(staff);
    }

    @Override
    @Cacheable(cacheNames = "StaffInfoByID", key = "#id")
    public Staff getStaffInfoByID(String id) throws NoSuchIdException, NullParameterException {
        logger.debug("getStaffInfoByID::要查找的职工ID为->" + id);
        if (StringUtils.isBlank(id)) {
            logger.warn("getStaffInfoByID::参数ID为空");
            throw new NullParameterException("参数ID为空");
        }
        if (!staffDao.exists(id)) {
            logger.warn("getStaffInfoByID::职工ID:" + id + "不存在");
            throw new NoSuchIdException("职工ID:" + id + "不存在");
        }
        return staffDao.findOne(id);
    }

    @Override
    @CacheEvict(cacheNames = {"StaffInfoList"}, key = "#staff.department.id")
    public void delStaffInfoByID(Staff staff) {
        logger.debug("delStaffInfoByID::删除员工ID" + staff.getId() + "的工资信息");
        wageDao.deleteByStaff(staff);
        logger.debug("delStaffInfoByID::删除员工ID" + staff.getId() + "信息");
        staffDao.delete(staff);
    }

    @Override
    public List<Staff> searchStaff(SearchStaff searchStaff) {
        logger.debug("searchStaff::开始搜索职工");
        logger.info("searchStaff::搜索实体信息->" + searchStaff);
        //如果选了多个部门并且选了某个部门下的基层单位
        if (searchStaff.getDepartment() != null && searchStaff.getDepartment().length != 1 && searchStaff.getGrassroot() != null) {
            logger.info("searchStaff::开始添加选中部门但未选中基层单位的部门下所有基层单位");
            a:
            for (String departmentID : searchStaff.getDepartment()) {
                Department department = departmentDao.getOne(departmentID);
                for (String g : searchStaff.getGrassroot()) {
                    //选中的基层单位与选中的部门下所有基层单位比较如果有相同则换部门
                    for (Grassroot grassroot : department.getGrassroots()) {
                        if (g.equals(grassroot.getId())) {
                            continue a;
                        }
                    }
                }
                searchStaff.setGrassroot(ArrayUtils.addAll(searchStaff.getGrassroot(), department.getGrassroots().stream().map(Grassroot::getId).toArray(String[]::new)));
            }
            logger.info("searchStaff::完成添加选中部门但未选中基层单位的部门下所有基层单位");
        }
        return staffDao.findAll((root, query, cb) -> {
            List<Predicate> list = new ArrayList<>();
            //查询条件:姓名(Name)
            if (StringUtils.isNoneBlank(searchStaff.getName())) {
                logger.info("searchStaff::查询条件 name(模糊查询)->" + searchStaff.getName());
                list.add(cb.like(root.get("name"), "%" + searchStaff.getName() + "%"));
            }
            //查询条件:银行卡号(bankID)
            if (StringUtils.isNoneBlank(searchStaff.getBankID())) {
                logger.info("searchStaff::查询条件 bankID(模糊查询)->" + searchStaff.getBankID());
                list.add(cb.like(root.get("bankID"), "%" + searchStaff.getBankID() + "%"));
            }
            //查询条件:邮箱(email)
            if (StringUtils.isNoneBlank(searchStaff.getEmail())) {
                logger.info("searchStaff::查询条件 email(模糊查询)->" + searchStaff.getEmail());
                list.add(cb.like(root.get("email"), "%" + searchStaff.getEmail() + "%"));
            }
            //查询条件:性别(sex)
            if (searchStaff.getSex() != null && searchStaff.getSex().length == 1) {
                logger.info("searchStaff::查询条件 sex(精确查询)->" + searchStaff.getSex()[0]);
                list.add(cb.equal(root.get("sex"), searchStaff.getSex()[0]));
            }
            //查询条件:身份证号(nid)
            if (StringUtils.isNoneBlank(searchStaff.getNid())) {
                logger.info("searchStaff::查询条件 nid(模糊查询)->" + searchStaff.getNid());
                list.add(cb.like(root.get("nid"), "%" + searchStaff.getNid() + "%"));
            }
            //查询条件:电话号码(tel)
            if (StringUtils.isNoneBlank(searchStaff.getTel())) {
                logger.info("searchStaff::查询条件 tel(模糊查询)->" + searchStaff.getTel());
                list.add(cb.like(root.get("tel"), "%" + searchStaff.getTel() + "%"));
            }
            //查询条件:部门ID(department)
            if (searchStaff.getDepartment() != null) {
                logger.info("searchStaff::查询条件 department(多条件查询)->" + Arrays.toString(searchStaff.getDepartment()));
                list.add(multipleConditionsQuery(logger, departmentDao, cb, root, "department", searchStaff.getDepartment()));
            }
            //查询条件:基层单位ID(grassroot)
            if (searchStaff.getGrassroot() != null) {
                logger.info("searchStaff::查询条件 grassroot(多条件查询)->" + Arrays.toString(searchStaff.getGrassroot()));
                list.add(multipleConditionsQuery(logger, grassrootDao, cb, root, "grassroot", searchStaff.getGrassroot()));
            }
            //查询条件:岗位名称(positionTitle)
            if (searchStaff.getPositionTitle() != null) {
                logger.info("searchStaff::查询条件 positionTitle(多条件查询)->" + Arrays.toString(searchStaff.getPositionTitle()));
                list.add(multipleConditionsQuery(logger, positionTitleDao, cb, root, "positionTitle", searchStaff.getPositionTitle()));
            }
            //查询条件:岗位类别(positionCategory)
            if (searchStaff.getPositionCategory() != null) {
                logger.info("searchStaff::查询条件 positionCategory(多条件查询)->" + Arrays.toString(searchStaff.getPositionCategory()));
                list.add(multipleConditionsQuery(logger, positionCategoryDao, cb, root, "positionCategory", searchStaff.getPositionCategory()));
            }
            //查询条件:出生日期(birthday)
            if (searchStaff.getStartBirthday() != null || searchStaff.getEndBirthday() != null) {
                logger.info("searchStaff::查询条件 birthday(日期区间查询)->" + searchStaff.getStartBirthday() + "\t" + searchStaff.getEndBirthday());
                dateIntervalQuery(logger, list, cb, root, "birthday", searchStaff.getStartBirthday(), searchStaff.getEndBirthday());
            }
            //查询条件:来校日期(comeDate)
            if (searchStaff.getStartComeDate() != null || searchStaff.getEndComeDate() != null) {
                logger.info("searchStaff::查询条件 comeDate(日期区间查询)->" + searchStaff.getStartComeDate() + "\t" + searchStaff.getEndComeDate());
                dateIntervalQuery(logger, list, cb, root, "comeDate", searchStaff.getStartComeDate(), searchStaff.getEndComeDate());
            }
            //查询条件:工龄起始日期(startDate)
            if (searchStaff.getStartDate() != null || searchStaff.getEndDate() != null) {
                logger.info("searchStaff::查询条件 startDate(日期区间查询)->" + searchStaff.getStartDate() + "\t" + searchStaff.getEndDate());
                dateIntervalQuery(logger, list, cb, root, "startDate", searchStaff.getStartDate(), searchStaff.getEndDate());
            }
            //查询条件:社会职称(jobTitle)
            if (searchStaff.getJobTitle() != null) {
                logger.info("searchStaff::查询条件 jobTitle(多条件查询)->" + Arrays.toString(searchStaff.getJobTitle()));
                list.add(multipleConditionsQuery(logger, jobTitleDao, cb, root, "jobTitle", searchStaff.getJobTitle()));
            }
            //查询条件:职称级别(jobLevel)
            if (searchStaff.getJobLevel() != null) {
                logger.info("searchStaff::查询条件 jobLevel(多条件查询)->" + Arrays.toString(searchStaff.getJobLevel()));
                list.add(multipleConditionsQuery(logger, jobLevelDao, cb, root, "jobLevel", searchStaff.getJobLevel()));
            }
            //查询条件:岗位工资(wage)
            if (searchStaff.getStartWage() != null || searchStaff.getEndWage() != null) {
                logger.info("searchStaff::查询条件 wage(字符串区间查询)->" + searchStaff.getStartWage() + "\t" + searchStaff.getEndWage());
                intIntervalQuery(logger, list, cb, root, "wage", searchStaff.getStartWage(), searchStaff.getEndWage());
            }
            //查询条件:绩效工资(performancePay)
            if (searchStaff.getStartPerformancePay() != null || searchStaff.getEndPerformancePay() != null) {
                logger.info("searchStaff::查询条件 performancePay(字符串区间查询)->" + searchStaff.getStartPerformancePay() + "\t" + searchStaff.getEndPerformancePay());
                intIntervalQuery(logger, list, cb, root, "performancePay", searchStaff.getStartPerformancePay(), searchStaff.getEndPerformancePay());
            }
            Predicate[] p = new Predicate[list.size()];
            return cb.and(list.toArray(p));
        });
    }

    @Override
    public void downStaffInfoByID(ServletOutputStream servletOutputStream, String... id) throws NoSuchIdException, IOException {
        for (String s : id) {
            if (!staffDao.exists(s)) {
                logger.warn("downStaffInfoByID::职工ID:" + s + "没有找到");
                throw new NoSuchIdException("职工ID:" + s + "没有找到");
            }
        }
        Workbook workbook = new XSSFWorkbook();
        //创建工作簿
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(1);
        logger.debug("downStaffInfoByID::准备标题数据");
        List<String> titleList = new ArrayList<>();
        titleList.add("序号");
        titleList.add("姓名");
        titleList.add("性别");
        titleList.add("民族");
        titleList.add("政治面貌");
        titleList.add("出生日期");
        titleList.add("身份证号码");
        titleList.add("年龄");
        titleList.add("户籍地址");
        titleList.add("现住址");
        titleList.add("电话号码");
        titleList.add("部门");
        titleList.add("专业/基层单位");
        titleList.add("岗位名称");
        titleList.add("岗位类别");
        titleList.add("备注");
        titleList.add("来校日期");
        titleList.add("校龄 ");
        titleList.add("工龄起始日期");
        titleList.add("工龄");
        titleList.add("社会职称");
        titleList.add("职称级别");
        titleList.add("职称授予专业");
        titleList.add("获证时间");
        titleList.add("职业资格证（1）");
        titleList.add("发证单位");
        titleList.add("获证时间");
        titleList.add("职业资格证（2）");
        titleList.add("认定专业");
        titleList.add("岗前培训证");
        titleList.add("岗前培训获得时间");
        titleList.add("岗位工资");
        titleList.add("绩效工资");
        titleList.add("职务津贴");
        titleList.add("岗位超时补助");
        titleList.add("硕士津贴");
        titleList.add("电话补助");
        titleList.add("应发额");
        titleList.add("用工形式");
        titleList.add("用工起始时间");
        titleList.add("第一次签约劳动合同期限");
        titleList.add("第一次签约到期日");
        titleList.add("第二次签约劳动合同期限");
        titleList.add("第二次签约到期日");
        titleList.add("第三次签约劳动合同期限");
        titleList.add("第三次签约到期日");
        titleList.add("第一学历[1]");
        titleList.add("获得学位");
        titleList.add("性质[1]");
        titleList.add("毕业时间[1]");
        titleList.add("毕业学校[1]");
        titleList.add("专业名称[1]");
        titleList.add("最高学历");
        titleList.add("最高学位");
        titleList.add("性质[2]");
        titleList.add("毕业时间[2]");
        titleList.add("毕业学校[2]");
        titleList.add("最高学历学缘结构");
        titleList.add("专业名称[2]");
        titleList.add("外语语种");
        titleList.add("外语等级");
        titleList.add("其他证书");
        logger.debug("downStaffInfoByID::标题数据集合大小->" + titleList.size());
        final int[] nowCell = {0};
        logger.debug("downStaffInfoByID::开始写入标题数据");
        titleList.forEach(s -> {
            Cell cell = row.createCell(nowCell[0]++);
            logger.debug("downStaffInfoByID::已创建Cell->" + (nowCell[0] - 1));
            cell.setCellValue(s);
            logger.debug("downStaffInfoByID::写入标题数据->" + s);
        });
        nowCell[0] = 2;
        for (String s : id) {
            Staff staff = staffDao.getOne(s);
            Row dataRow = sheet.createRow(nowCell[0]++);
            logger.debug("downStaffInfoByID::已创建Cell->" + (nowCell[0] - 1));
            logger.debug("downStaffInfoByID::开始写入");
            Cell numCell = dataRow.createCell(0);
            numCell.setCellValue(nowCell[0] - 2);

            Cell nameCell = dataRow.createCell(1);
            nameCell.setCellValue(staff.getName());

            Cell sexCell = dataRow.createCell(2);
            sexCell.setCellValue(staff.isSex() ? "男" : "女");

            Cell ethnicCell = dataRow.createCell(3);
            ethnicCell.setCellValue(staff.getEthnic().getName());

            Cell politicalStatusCell = dataRow.createCell(4);
            politicalStatusCell.setCellValue(staff.getPs().getName());

            Cell birthdayCell = dataRow.createCell(5);
            birthdayCell.setCellValue(dateFormat(staff.getBirthday()));

            Cell nidCell = dataRow.createCell(6);
            nidCell.setCellValue(staff.getNid());

            Cell ageCell = dataRow.createCell(7);
            ageCell.setCellValue(staff.getAge());

            Cell addressCell = dataRow.createCell(8);
            addressCell.setCellValue(staff.getAddress());

            Cell naddressCell = dataRow.createCell(9);
            naddressCell.setCellValue(staff.getNaddress());

            Cell telCell = dataRow.createCell(10);
            telCell.setCellValue(staff.getTel());

            Cell departmentCell = dataRow.createCell(11);
            departmentCell.setCellValue(staff.getDepartment().getName());

            Cell grassrootCell = dataRow.createCell(12);
            grassrootCell.setCellValue(staff.getGrassroot().getName());

            Cell positionTitleCell = dataRow.createCell(13);
            positionTitleCell.setCellValue(staff.getPositionTitle().getName());

            Cell positionCategoryCell = dataRow.createCell(14);
            positionCategoryCell.setCellValue(staff.getPositionCategory().getName());

            Cell marksCell = dataRow.createCell(15);
            marksCell.setCellValue(staff.getMarks());

            Cell comeDateCell = dataRow.createCell(16);
            comeDateCell.setCellValue(dateFormat(staff.getComeDate()));

            Cell comeDateYearCell = dataRow.createCell(17);
            try {
                comeDateYearCell.setCellValue(StaffUtils.getFormatTime(staff.getComeDate()));
            } catch (IllegalParametersException e) {
                e.printStackTrace();
            }

            Cell startDateCell = dataRow.createCell(18);
            startDateCell.setCellValue(dateFormat(staff.getStartDate()));

            Cell startDateYearCell = dataRow.createCell(19);
            try {
                startDateYearCell.setCellValue(StaffUtils.getFormatTime(staff.getStartDate()));
            } catch (IllegalParametersException e) {
                e.printStackTrace();
            }

            Cell jobTitleCell = dataRow.createCell(20);
            jobTitleCell.setCellValue(staff.getJobTitle().getName());

            Cell jobLevelCell = dataRow.createCell(21);
            jobLevelCell.setCellValue(staff.getJobLevel().getName());

            Cell rtaCell = dataRow.createCell(22);
            rtaCell.setCellValue(staff.getRta());

            Cell certifiedTimeCell = dataRow.createCell(23);
            certifiedTimeCell.setCellValue(dateFormat(staff.getCertifiedTime()));

            Cell oqc1Cell = dataRow.createCell(24);
            oqc1Cell.setCellValue(staff.getOqc1());

            Cell issuingUnitCell = dataRow.createCell(25);
            issuingUnitCell.setCellValue(staff.getIssuingUnit());

            Cell oqc1TimeCell = dataRow.createCell(26);
            oqc1TimeCell.setCellValue(dateFormat(staff.getOqc1Time()));

            Cell oqc2Cell = dataRow.createCell(27);
            oqc2Cell.setCellValue(staff.getOqc2());

            Cell cpCell = dataRow.createCell(28);
            cpCell.setCellValue(staff.getCp());

            Cell ptcCell = dataRow.createCell(29);
            ptcCell.setCellValue(staff.getPtc());

            Cell ptcTimeCell = dataRow.createCell(30);
            ptcTimeCell.setCellValue(dateFormat(staff.getPtcTime()));

            Cell wageCell = dataRow.createCell(31);
            wageCell.setCellValue(staff.getWage());

            Cell performancePayCell = dataRow.createCell(32);
            performancePayCell.setCellValue(staff.getPerformancePay());

            Cell dutyAllowanceCell = dataRow.createCell(33);
            dutyAllowanceCell.setCellValue(staff.getDutyAllowance() == null ? 0 : staff.getDutyAllowance());

            Cell grantsCell = dataRow.createCell(34);
            grantsCell.setCellValue(staff.getGrants() == null ? 0 : staff.getGrants());

            Cell mAllowanceCell = dataRow.createCell(35);
            mAllowanceCell.setCellValue(staff.getMAllowance() == null ? 0 : staff.getMAllowance());

            Cell pSubsidiesCell = dataRow.createCell(36);
            pSubsidiesCell.setCellValue(staff.getPSubsidies() == null ? 0 : staff.getPSubsidies());

            Cell yingfaCell = dataRow.createCell(37);
            yingfaCell.setCellValue(staff.getWage() + staff.getPerformancePay() + (staff.getDutyAllowance() == null ? 0 : staff.getDutyAllowance()) + (staff.getGrants() == null ? 0 : staff.getGrants()) + (staff.getMAllowance() == null ? 0 : staff.getMAllowance()) + (staff.getPSubsidies() == null ? 0 : staff.getPSubsidies()));

            Cell employmentFormCell = dataRow.createCell(38);
            employmentFormCell.setCellValue(staff.getEmploymentForm().getName());

            Cell eStartDateCell = dataRow.createCell(39);
            eStartDateCell.setCellValue(dateFormat(staff.getEStartDate()));

            Cell laborContract1Cell = dataRow.createCell(40);
            laborContract1Cell.setCellValue(dateFormat(staff.getLaborContract1()));

            Cell laborContract1EndCell = dataRow.createCell(41);
            laborContract1EndCell.setCellValue(dateFormat(staff.getLaborContract1End()));

            Cell laborContract2Cell = dataRow.createCell(42);
            laborContract2Cell.setCellValue(dateFormat(staff.getLaborContract2()));

            Cell laborContract2EndCell = dataRow.createCell(43);
            laborContract2EndCell.setCellValue(dateFormat(staff.getLaborContract2End()));

            Cell laborContract3Cell = dataRow.createCell(44);
            laborContract3Cell.setCellValue(dateFormat(staff.getLaborContract3()));

            Cell laborContract3EndCell = dataRow.createCell(45);
            laborContract3EndCell.setCellValue(dateFormat(staff.getLaborContract3End()));

            Cell ducation1Cell = dataRow.createCell(46);
            ducation1Cell.setCellValue(staff.getDucation1());

            Cell bsCell = dataRow.createCell(47);
            bsCell.setCellValue(staff.getBs());

            Cell nature1Cell = dataRow.createCell(48);
            nature1Cell.setCellValue(staff.getNature1());

            Cell graduationTime1Cell = dataRow.createCell(49);
            graduationTime1Cell.setCellValue(dateFormat(staff.getGraduationTime1()));

            Cell graduatedSchool1Cell = dataRow.createCell(50);
            graduatedSchool1Cell.setCellValue(staff.getGraduatedSchool1());

            Cell professionalTitle1Cell = dataRow.createCell(51);
            professionalTitle1Cell.setCellValue(staff.getProfessionalTitle1());

            Cell highestEducationCell = dataRow.createCell(52);
            highestEducationCell.setCellValue(staff.getHighestEducation());

            Cell hghestDegreeCell = dataRow.createCell(53);
            hghestDegreeCell.setCellValue(staff.getHghestDegree());

            Cell nature2Cell = dataRow.createCell(54);
            nature2Cell.setCellValue(staff.getNature2());

            Cell graduationTime2Cell = dataRow.createCell(55);
            graduationTime2Cell.setCellValue(dateFormat(staff.getGraduationTime2()));

            Cell graduatedSchool2Cell = dataRow.createCell(56);
            graduatedSchool2Cell.setCellValue(staff.getGraduatedSchool2());

            Cell thCell = dataRow.createCell(57);
            thCell.setCellValue(staff.getTh());

            Cell professionalTitle2Cell = dataRow.createCell(58);
            professionalTitle2Cell.setCellValue(staff.getProfessionalTitle2());

            Cell foreignLanguageCell = dataRow.createCell(59);
            foreignLanguageCell.setCellValue(staff.getForeignLanguage());

            Cell flLevelCell = dataRow.createCell(60);
            flLevelCell.setCellValue(staff.getFlLevel());

            Cell otherCertificatesCell = dataRow.createCell(61);
            otherCertificatesCell.setCellValue(staff.getOtherCertificates());
        }
        workbook.write(servletOutputStream);
        logger.debug("downStaffInfoByID::workbook写入到输出流完成");
        workbook.close();
        logger.debug("downStaffInfoByID::workbook已关闭");
    }

    /**
     * 日期格式化
     *
     * @param date 日期
     * @return 如果data为null则返回"" 反之格式化 yyyy-MM-dd
     */
    private String dateFormat(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}