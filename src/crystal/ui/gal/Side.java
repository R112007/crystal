package crystal.ui.gal;

/**
 * 立绘在屏幕上的位置，用于区分当前说话角色所在的侧边。
 */
public enum Side {
    /** 左侧立绘（默认） */
    left,
    /** 右侧立绘 */
    right,
    /** 两侧同时存在但不以某侧为主 */
    both,
    /** 无立绘 */
    none
}
