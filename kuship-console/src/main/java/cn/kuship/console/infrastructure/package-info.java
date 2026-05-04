/**
 * 基础设施层：JPA 公共能力、Region API client、kubernetes-client 封装。
 *
 * <p>本 change 仅建立包结构占位：
 * <ul>
 *   <li>{@code jpa/}     —— BaseEntity / 审计 / 命名策略辅助类，由后续 change 引入</li>
 *   <li>{@code region/}  —— Region API client（移植 rainbond-console 的 regionapi.py 3850 行），独立 epic：
 *                            {@code migrate-console-region-client}</li>
 *   <li>{@code k8s/}     —— kubernetes-client/java 封装，仅 rke2 集群纳管模块使用，由
 *                            {@code migrate-console-region-cluster} 引入</li>
 * </ul>
 */
package cn.kuship.console.infrastructure;
