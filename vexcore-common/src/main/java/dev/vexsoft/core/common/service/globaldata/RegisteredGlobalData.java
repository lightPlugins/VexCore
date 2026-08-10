package dev.vexsoft.core.common.service.globaldata;

import dev.vexsoft.core.api.globaldata.GlobalDataKey;
import dev.vexsoft.core.common.data.global.GlobalDataReference;

/** Binds an owner-qualified persistence reference to its public typed key. */
record RegisteredGlobalData(GlobalDataReference reference, GlobalDataKey<?> key) { }
