/*
 * PosixSched.hpp
 *
 * Copyright (C) 2022 by Posit Software, PBC
 *
 * Unless you have received this program directly from Posit Software pursuant
 * to the terms of a commercial license agreement with Posit Software, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

#ifndef CORE_SYSTEM_POSIX_SCHED_HPP
#define CORE_SYSTEM_POSIX_SCHED_HPP

#include <vector>

namespace rstudio {
namespace core {

class Error;

namespace system {

typedef std::vector<bool> CpuAffinity;

int cpuCount();
CpuAffinity emptyCpuAffinity();
bool isCpuAffinityEmpty(const CpuAffinity& cpus);
Error getCpuAffinity(CpuAffinity* pCpus);
Error setCpuAffinity(const CpuAffinity& cpus);

} // namespace system
} // namespace core
} // namespace rstudio

#endif // CORE_SYSTEM_POSIX_SCHED_HPP

