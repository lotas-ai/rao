

# shared cpack variables
set(CPACK_PACKAGE_NAME "Rao")
set(CPACK_PACKAGE_DESCRIPTION "Rao")
set(CPACK_PACKAGE_VENDOR "Posit Software")
set(CPACK_PACKAGE_CONTACT "Rao <willanickols@gmail.com>")
set(CPACK_PACKAGE_INSTALL_DIRECTORY "Rao")

if(WIN32)

   add_subdirectory(win32)

elseif(APPLE)

   add_subdirectory(osx)

elseif(LINUX)

   add_subdirectory(linux)

endif()
