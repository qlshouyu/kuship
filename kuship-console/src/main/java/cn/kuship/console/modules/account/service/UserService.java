package cn.kuship.console.modules.account.service;

import cn.kuship.console.common.exception.ServiceHandleException;
import cn.kuship.console.common.util.UuidGenerator;
import cn.kuship.console.modules.account.dto.RegisterReq;
import cn.kuship.console.modules.account.entity.PermRelTenant;
import cn.kuship.console.modules.account.entity.TenantEnterprise;
import cn.kuship.console.modules.account.entity.Tenants;
import cn.kuship.console.modules.account.entity.UserInfo;
import cn.kuship.console.modules.account.password.LegacyPasswordEncoder;
import cn.kuship.console.modules.account.repository.PermRelTenantRepository;
import cn.kuship.console.modules.account.repository.TenantEnterpriseRepository;
import cn.kuship.console.modules.account.repository.TenantsRepository;
import cn.kuship.console.modules.account.repository.UserInfoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private final UserInfoRepository userRepo;
    private final TenantsRepository tenantsRepo;
    private final TenantEnterpriseRepository enterpriseRepo;
    private final PermRelTenantRepository permRelTenantRepo;
    private final LegacyPasswordEncoder passwordEncoder;

    public UserService(UserInfoRepository userRepo,
                       TenantsRepository tenantsRepo,
                       TenantEnterpriseRepository enterpriseRepo,
                       PermRelTenantRepository permRelTenantRepo,
                       LegacyPasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.tenantsRepo = tenantsRepo;
        this.enterpriseRepo = enterpriseRepo;
        this.permRelTenantRepo = permRelTenantRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public UserInfo authenticate(String nickName, String rawPassword) {
        UserInfo user = userRepo.findByNickName(nickName)
                .orElseThrow(() -> new ServiceHandleException(400, "user not found", "用户名或密码错误"));
        String hashed = passwordEncoder.encode(user.getEmail() + rawPassword);
        if (!hashed.equals(user.getPassword())) {
            throw new ServiceHandleException(400, "password mismatch", "用户名或密码错误");
        }
        if (Boolean.FALSE.equals(user.getActive())) {
            throw new ServiceHandleException(403, "user disabled", "账号已被禁用");
        }
        return user;
    }

    @Transactional
    public UserInfo register(RegisterReq req) {
        if (userRepo.existsByNickName(req.nickName())) {
            throw new ServiceHandleException(400, "nick_name already exists", "用户名已被占用");
        }
        if (userRepo.existsByEmail(req.email())) {
            throw new ServiceHandleException(400, "email already exists", "邮箱已被占用");
        }
        TenantEnterprise enterprise = enterpriseRepo.findFirstByIsActiveOrderByIdAsc(1)
                .orElseGet(() -> {
                    TenantEnterprise e = new TenantEnterprise();
                    e.setEnterpriseId(UuidGenerator.makeUuid());
                    e.setEnterpriseName("default");
                    e.setEnterpriseAlias("默认企业");
                    e.setIsActive(1);
                    e.setEnableTeamResourceView(true);
                    e.setCreateTime(LocalDateTime.now());
                    return enterpriseRepo.save(e);
                });

        UserInfo user = new UserInfo();
        user.setNickName(req.nickName());
        user.setEmail(req.email());
        user.setPhone(req.phone());
        user.setRealName(req.realName());
        user.setActive(true);
        user.setSysAdmin(false);
        user.setEnterpriseId(enterprise.getEnterpriseId());
        user.setCreateTime(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(req.email() + req.password()));
        UserInfo saved = userRepo.save(user);

        // 创建默认 team
        Tenants team = new Tenants();
        team.setTenantId(UuidGenerator.makeTenantId());
        team.setTenantName(req.nickName() + "-default");
        team.setNamespace(req.nickName() + "-default");
        team.setTenantAlias(req.nickName() + " 默认团队");
        team.setActive(true);
        team.setCreater(saved.getUserId());
        team.setEnterpriseId(enterprise.getEnterpriseId());
        team.setLimitMemory(1024);
        team.setCreateTime(LocalDateTime.now());
        team.setUpdateTime(LocalDateTime.now());
        Tenants savedTeam = tenantsRepo.save(team);

        // 加入团队（owner 身份）
        PermRelTenant rel = new PermRelTenant();
        rel.setUserId(saved.getUserId());
        rel.setTenantId(savedTeam.getId());
        rel.setIdentity("owner");
        rel.setEnterpriseId(enterprise.getId());
        permRelTenantRepo.save(rel);

        return saved;
    }

    @Transactional
    public void changePassword(Integer userId, String oldRaw, String newRaw) {
        UserInfo user = userRepo.findById(userId)
                .orElseThrow(() -> new ServiceHandleException(404, "user not found", "用户不存在"));
        String oldHashed = passwordEncoder.encode(user.getEmail() + oldRaw);
        if (!oldHashed.equals(user.getPassword())) {
            throw new ServiceHandleException(400, "old password mismatch", "旧密码错误");
        }
        if (newRaw.length() < 8) {
            throw new ServiceHandleException(400, "new password too short", "新密码长度需至少 8 位");
        }
        user.setPassword(passwordEncoder.encode(user.getEmail() + newRaw));
        userRepo.save(user);
    }

    public Optional<UserInfo> findById(Integer userId) {
        return userRepo.findById(userId);
    }

    public Page<UserInfo> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepo.findAll(pageable);
        }
        return userRepo.search(query, pageable);
    }
}
