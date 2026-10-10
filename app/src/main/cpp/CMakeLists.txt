cmake_minimum_required(VERSION 3.22.1)
project(tunnel_engine C)

set(CMAKE_C_STANDARD 99)

include(FetchContent)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(MBEDTLS_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
  mbedtls
  URL https://github.com/Mbed-TLS/mbedtls/archive/refs/tags/v2.28.9.tar.gz
)
FetchContent_MakeAvailable(mbedtls)

if(TARGET mbedcrypto)
  target_compile_definitions(mbedcrypto PRIVATE MBEDTLS_MD4_C)
endif()

add_library(
  tunnel_engine
  SHARED
  vpn_jni.c
  tunnel_loop.c
  packet_endpoint.c
  gvisor_bridge.c
  ipv4_dataplane.c
  nat_t_keepalive.c
  util.c
  util_endian.c
  modp2048_data.c
  ikev1.c
  esp_udp.c
  l2tp_avps.c
  l2tp.c
  ppp_mschap.c
  ppp.c
)

target_include_directories(
  tunnel_engine
  PRIVATE
    ${mbedtls_SOURCE_DIR}/include
)

target_link_libraries(
  tunnel_engine
  PRIVATE mbedtls mbedx509 mbedcrypto log dl
)

option(TUNNEL_FORGE_ESP_INNER_UDP_NO_CHECKSUM "Use inner UDP checksum 0 (interop)" ON)
if(TUNNEL_FORGE_ESP_INNER_UDP_NO_CHECKSUM)
  target_compile_definitions(tunnel_engine PRIVATE TUNNEL_FORGE_ESP_INNER_UDP_NO_CHECKSUM=1)
endif()

set(TUNNEL_FORGE_KEYMAT_VARIANT "1" CACHE STRING "Active KEYMAT variant (1=A,2=B,3=C)")
target_compile_definitions(tunnel_engine PRIVATE TUNNEL_FORGE_KEYMAT_VARIANT=${TUNNEL_FORGE_KEYMAT_VARIANT})
