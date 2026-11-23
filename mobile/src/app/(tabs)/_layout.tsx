import Ionicons from '@expo/vector-icons/Ionicons';
import { Tabs } from 'expo-router';
import type { ComponentProps } from 'react';
import type { ColorValue } from 'react-native';

import { colors } from '@/theme';

type IconName = ComponentProps<typeof Ionicons>['name'];
type IconColor = ComponentProps<typeof Ionicons>['color'];   // the tab bar only passes the tint colours set below

function icon(name: IconName) {
  return function TabIcon({ color, size }: { color: ColorValue; size: number }) {
    return <Ionicons name={name} size={size} color={color as IconColor} />;
  };
}

export default function TabLayout() {
  return (
    <Tabs screenOptions={{ tabBarActiveTintColor: colors.primary, headerTintColor: colors.text }}>
      <Tabs.Screen name="index" options={{ title: 'Near me', headerTitle: 'FloodGuard', tabBarIcon: icon('location') }} />
      <Tabs.Screen name="roads" options={{ title: 'Roads', tabBarIcon: icon('car') }} />
      <Tabs.Screen name="alerts" options={{ title: 'Official alerts', tabBarIcon: icon('megaphone') }} />
      <Tabs.Screen name="account" options={{ title: 'SMS alerts', tabBarIcon: icon('chatbubble-ellipses') }} />
    </Tabs>
  );
}
