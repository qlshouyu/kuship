/**
 * 业务模块根包。本 change 仅建立空目录占位，业务代码由后续 12 个 change 增量交付。
 *
 * <h2>预期模块清单（与迁移路线图对应）</h2>
 * <ul>
 *   <li>{@code account}     —— 用户、AccessToken、OAuth、登录事件（migrate-console-account-team）</li>
 *   <li>{@code team}        —— 团队、租户、权限、角色（migrate-console-account-team）</li>
 *   <li>{@code region}      —— 集群、Registry、k8s_attribute、k8s_resource、rke2 纳管
 *                              （migrate-console-region-cluster）</li>
 *   <li>{@code application} —— group、application、app_overview、app_config、app_create、
 *                              app_manage、app_event、app_monitor、app_pod、app_log、app_autoscaler、
 *                              app_version、app_upgrade（migrate-console-application-* 系列）</li>
 *   <li>{@code plugin}      —— plugin / platform_plugin（migrate-console-plugin）</li>
 *   <li>{@code market}      —— 应用市场、share、backup、helm_app（migrate-console-app-market）</li>
 *   <li>{@code misc}        —— 升级、消息、webhook、mcp、文件上传等（migrate-console-misc）</li>
 * </ul>
 */
package cn.kuship.console.modules;
